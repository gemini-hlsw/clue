package clue.fsm

import clue._

import clue.GraphQLStreamingClient
import cats.effect.concurrent.Ref
import cats.effect.implicits._
import sttp.model.Uri
import clue.GraphQLSubscription
import io.circe._
import io.circe.parser._

import cats.syntax.all._
import cats.effect.concurrent.Deferred
import java.util.UUID
import cats.effect.ConcurrentEffect
import io.chrisdavenport.log4cats.Logger
import cats.effect.ExitCase
import cats.effect.Timer
import clue.model.StreamingMessage
import clue.model.json._

// Client facing interface
trait ApolloClient[F[_], S, CP] extends GraphQLStreamingClient[F, S] {
  def connect(): F[Unit]
  def initialize(payload: F[Map[String, Json]]): F[Unit]

  def terminate(): F[Unit]
  def disconnect(closeParameters: CP): F[Unit]
  def disconnect(): F[Unit]
}

// Backend facing interface
trait WebSocketHandler[F[_], CE] {
  protected def onMessage(msg: String): F[Unit]
  protected def onClose(event: CE): F[Unit]
}

// Internal interface
trait StreamingClientInternal[F[_]] {
  protected def stopSubscription(id: ApolloClient.SubscriptionId): F[Unit]
}

protected sealed trait State[+F[_], +CP]
protected object State {
  final case object Disconnected extends State[Nothing, Nothing]
  final case class Connecting[F[_]](latch: Deferred[F, Either[Throwable, Unit]])
      extends State[F, Nothing]
  final case class Connected[F[_], CP](connection: PersistentConnection[F, CP]) extends State[F, CP]
  final case class Initializing[F[_], CP](
    connection: PersistentConnection[F, CP],
    latch:      Deferred[F, Either[Throwable, Unit]]
  )                              extends State[F, CP]
  final case class Initialized[F[_], CP](connection: PersistentConnection[F, CP])
      extends State[F, CP]
  // Reestablishing = We are in the process of reconnecting + reinitializing after a low level error/close, but we haven't connected yet.
  final case class Reestablishing[F[_]](
    connectLatch: Deferred[F, Either[Throwable, Unit]],
    initLatch:    Deferred[F, Either[Throwable, Unit]]
  )                              extends State[F, Nothing]

}

class ApolloClientImpl[F[_], S, CP, CE](
  uri:                  Uri,
  reconnectionStrategy: ReconnectionStrategy[CE],
  state:                Ref[F, State[F, CP]]
)(implicit
  F:                    ConcurrentEffect[F],
  backend:              PersistentBackend[F, CP, CE],
  timer:                Timer[F],
  logger:               Logger[F]
) extends ApolloClient[F, S, CP]
    with WebSocketHandler[F, CE]
    with StreamingClientInternal[F] {
  import State._

  override def connect(): F[Unit] = {
    val warn = "connect() called while already connected or attempting to connect.".warnF

    Deferred[F, Either[Throwable, Unit]].flatMap { newLatch =>
      state.modify {
        case Disconnected                        => Connecting(newLatch) -> doConnect(newLatch)
        case s @ Connecting(latch)               => s                    -> (warn >> latch.get.rethrow)
        case s @ Reestablishing(connectLatch, _) => s                    -> (warn >> connectLatch.get.rethrow)
        case state                               => state                -> warn
      }.flatten
    }
  }

  override def initialize(payload: F[Map[String, Json]]): F[Unit] = {
    val error = "initialize() called while disconnected.".raiseError
    val warn  = "initialize() called while already initialized or attempting to initialize.".warnF

    Deferred[F, Either[Throwable, Unit]].flatMap { newLatch =>
      state.modify {
        case s @ (Disconnected | Connecting(_)) => s     -> error
        case Connected(connection)              =>
          Initializing(connection, newLatch) -> doInitialize(payload, connection, newLatch)
        case s @ Initializing(_, latch)         => s     -> (warn >> latch.get.rethrow)
        case s @ Reestablishing(_, initLatch)   => s     -> (warn >> initLatch.get.rethrow)
        case state                              => state -> warn
      }.flatten
    }
  }

  override def terminate(): F[Unit] = {
    val error = "terminate() called while uninitialized.".raiseError
    val warn  = "terminate() called while initializing.".warnF

    state.modify {
      case Initialized(connection)          =>
        Connected(connection) -> connection.send(StreamingMessage.FromClient.ConnectionTerminate)
      case s @ Initializing(_, latch)       => s -> (warn >> latch.get.rethrow >> terminate())
      case s @ Reestablishing(_, initLatch) => s -> (warn >> initLatch.get.rethrow >> terminate())
      case s                                => s -> error
    }.flatten
    // .uncancelable // TODO We have waiting, we need to handle interruptions.
  }

  final def disconnect(closeParameters: CP): F[Unit] = disconnectInternal(closeParameters.some)

  final def disconnect(): F[Unit] = disconnectInternal(none)

  private def disconnectInternal(closeParameters: Option[CP]): F[Unit] = {
    val error            = "disconnect() called while disconnected.".raiseError
    val interruptedError = "disconnect() called while connecting or initializing.".error

    // We *could* wait for onClose to be invoked before completing, but is there a point to that?

    state
      .modify {
        case Connecting(latch)                       =>
          // We need a wait for the connection to establish and then disconnect it, without blocking the client.
          Disconnected -> latch.complete(
            interruptedError
          ) // >> TODO wait in background to complete and close
        case Connected(connection)                   => Disconnected -> connection.closeInternal(closeParameters)
        case Initializing(connection, latch)         =>
          Disconnected -> (latch.complete(interruptedError) >>
            connection.closeInternal(closeParameters))
        case Initialized(connection)                 => Disconnected -> connection.closeInternal(closeParameters)
        case Reestablishing(connectLatch, initLatch) =>
          Disconnected -> (connectLatch.complete(interruptedError).attempt.void >>
            initLatch.complete(
              interruptedError
            )) // >> TODO wait in background to complete and close
        case s                                       => s            -> error
      }
      .flatten
      .uncancelable
  }

  override protected def requestInternal[D: Decoder](
    document:      String,
    operationName: Option[String],
    variables:     Option[Json]
  ): F[D] = ???

  override protected def subscribeInternal[D: Decoder](
    document:      String,
    operationName: Option[String],
    variables:     Option[Json]
  ): F[GraphQLSubscription[F, D]] = ???

  override protected def stopSubscription(id: ApolloClient.SubscriptionId): F[Unit] = ???

  override protected def onMessage(msg: String): F[Unit] =
    decode[StreamingMessage.FromServer](msg) match {
      case Left(e)                                                       =>
        e.raiseF(s"Exception decoding message received from server: [$msg]")
      case Right(StreamingMessage.FromServer.ConnectionAck)              =>
        state.modify {
          case Initializing(connection, latch) =>
            (Initialized(connection) -> latch.complete(().asRight))
          case s                               => s -> s"Unexpected connection_ack received from server.".warnF
        }.flatten
      case Right(StreamingMessage.FromServer.ConnectionError(payload))   =>
        state.modify {
          case Initializing(connection, latch) =>
            (Connected(connection) -> latch.complete(
              s"Initialization rejected by server: [$payload].".error
            ))
          case s                               => s -> s"Unexpected connection_error received from server.".warnF
        }.flatten
      case Right(StreamingMessage.FromServer.Data(id @ _, payload @ _))  => F.unit
      case Right(StreamingMessage.FromServer.Error(id @ _, payload @ _)) => F.unit
      case Right(StreamingMessage.FromServer.Complete(id @ _))           => F.unit
      case _                                                             => F.unit
    }

  override protected def onClose(event: CE): F[Unit] =
    reconnectionStrategy(0, event.asRight) match {
      case None       =>
        state.modify {
          case s @ Disconnected => s            -> s"onClose() called while disconnected.".warnF
          case _                => Disconnected -> F.unit
        }.flatten
      case Some(wait) =>
        Deferred[F, Either[Throwable, Unit]].flatMap { newConnectionLatch =>
          Deferred[F, Either[Throwable, Unit]].flatMap { newInitLatch =>
            val waitF = s"Connection closed. Attempting to reconnect...".warnF >> timer.sleep(wait)

            state.modify {
              case s @ Disconnected                           =>
                s -> s"onClose() called while disconnected. Sice this is unexpected, reconnection strategy will not be applied.".warnF
              // Ignoring following case on purpose. Latch release, error propagation/reconnection should be handled by failed "doConnect()".
              case s @ (Connecting(_) | Reestablishing(_, _)) => s -> F.unit
              case Connected(_)                               =>
                Connecting(newConnectionLatch) -> (waitF >> doConnect(newConnectionLatch))
              case Initializing(_, latch)                     =>
                Reestablishing(newConnectionLatch, latch) ->
                  (waitF >> doConnect(newConnectionLatch))
              case Initialized(_)                             =>
                Reestablishing(newConnectionLatch, newInitLatch) ->
                  (waitF >> doConnect(newConnectionLatch))
            }.flatten
          }
        }
    }

  private def doConnect(
    latch:   Deferred[F, Either[Throwable, Unit]],
    attempt: Int = 1
  ): F[Unit] =
    backend
      .connect(uri, onMessage, onClose)
      .attempt
      .flatMap { connection =>
        state.modify {
          case s @ Connecting(_)                =>
            connection match {
              case Left(t)  =>
                reconnectionStrategy(attempt, t.asLeft) match {
                  case None       => Disconnected -> latch.complete(t.asLeft)
                  case Some(wait) =>
                    s -> (t.warnF(s"Error in connect() after attempt #[$attempt]. Retrying...") >>
                      timer.sleep(wait) >>
                      doConnect(latch, attempt + 1))
                }
              case Right(c) => Connected(c) -> latch.complete(().asRight)
            }
          case s @ Reestablishing(_, initLatch) =>
            connection match {
              case Left(t)  =>
                reconnectionStrategy(attempt, t.asLeft) match {
                  case None       =>
                    Disconnected -> (latch.complete(t.asLeft) >> initLatch.complete(t.asLeft))
                  case Some(wait) => // TODO Unifiy with same code as above.
                    s -> (t.warnF(s"Error in connect() after attempt #[$attempt]. Retrying...") >>
                      timer.sleep(wait) >>
                      doConnect(latch, attempt + 1))
                }
              case Right(c) =>
                Initializing(c, initLatch) -> (latch.complete(().asRight) >>
                  doInitialize(F.pure(Map.empty), c, initLatch)) // TODO PROPAGATE PAYLOAD GETTER!
            }
          case s                                =>
            s -> (latch.complete(connection.void) >>
              s"Unexpected state [$s] in connect(). Unblocking clients, but state may be inconsistent.".raiseError)
        }.flatten
      }
      .guaranteeCase {
        case ExitCase.Completed | ExitCase.Error(_) => F.unit
        case ExitCase.Canceled                      => // Attempt recovery.
          state.modify {
            case s @ Connected(_)             => s -> latch.complete(Either.unit).attempt.void
            case Connecting(_)                =>
              // TODO Cleanup the web socket. We should call .close() on it once it's connected. But we have to keep track of it.
              Disconnected -> latch
                .complete("connect() was canceled.".error[Unit])
                .attempt
                .void
            case Reestablishing(_, initLatch) =>
              // TODO Cleanup the web socket. We should call .close() on it once it's connected. But we have to keep track of it.
              Disconnected ->
                (latch.complete("connect() was canceled.".error[Unit]).attempt >>
                  initLatch.complete("connect() was canceled.".error[Unit]).attempt.void)
            case s                            =>
              s -> s"Unexpected state [$s] in canceled connect(). Cannot recover.".raiseError
          }.flatten
      }

  private def doInitialize(
    payload:    F[Map[String, Json]],
    connection: PersistentConnection[F, CP],
    latch:      Deferred[F, Either[Throwable, Unit]]
  ): F[Unit] = (for {
    p <- payload
    _ <- connection.send(StreamingMessage.FromClient.ConnectionInit(p))
    _ <- latch.get.rethrow
  } yield ()).guaranteeCase {
    case ExitCase.Completed | ExitCase.Error(_) => F.unit
    case ExitCase.Canceled                      => // Attempt recovery.
      state.modify {
        case s @ Initializing(_, _) =>
          s ->
            (disconnect().start >> latch
              .complete("initialize() was canceled. Disconnecting...".error[Unit])
              .attempt
              .void)
        case s                      =>
          s -> (disconnect().start >> s"Unexpected state [$s] in canceled initialize(). Cannot recover. Disconnecting...".raiseError)
      }.flatten
  }

}

object ApolloClient {
  type SubscriptionId = UUID

  def apply[F[_], S, CP, CE](
    uri:                  Uri,
    name:                 String = "",
    reconnectionStrategy: ReconnectionStrategy[CE] = ReconnectionStrategy.never
  )(implicit
    F:                    ConcurrentEffect[F],
    backend:              PersistentBackend[F, CP, CE],
    timer:                Timer[F],
    logger:               Logger[F]
  ): F[ApolloClient[F, S, CP]] = {
    val logPrefix = s"clue.ApolloClient[${if (name.isEmpty) uri else name}]"

    for {
      state <- Ref[F].of[State[F, CP]](State.Disconnected)
    } yield new ApolloClientImpl(uri, reconnectionStrategy, state)(
      F,
      backend,
      timer,
      logger.withModifiedString(s => s"$logPrefix $s")
    )
  }
}
