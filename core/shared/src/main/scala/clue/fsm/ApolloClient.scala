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

trait ApolloClient[F[_], S] extends GraphQLStreamingClient[F, S] {
  def connect(): F[Unit]
  def initialize(payload: F[Map[String, Json]]): F[Unit]

  def terminate(): F[Unit]
  def disconnect(): F[Unit]
}

trait WebSocketHandler[F[_], CE] {
  protected def onMessage(msg: String): F[Unit]
  protected def onError(t:     Throwable): F[Unit]
  protected def onClose(event: CE): F[Unit]
}

trait StreamingClientInternal[F[_]] {
  // protected def sendMessage(msg:     StreamingMessage.FromClient): F[Unit]
  protected def stopSubscription(id: ApolloClient.SubscriptionId): F[Unit]
}

protected sealed trait State[+F[_], +CP]
protected object State {
  final case object Disconnected  extends State[Nothing, Nothing]
  final case class Connecting[F[_]](latch: Deferred[F, Either[Throwable, Unit]])
      extends State[F, Nothing]
  final case class Connected[F[_], CP](connection: PersistentConnection[F, CP]) extends State[F, CP]
  final case class Initializing[F[_], CP](
    connection: PersistentConnection[F, CP],
    latch:      Deferred[F, Either[Throwable, Unit]]
  )                               extends State[F, CP]
  final case class Initialized[F[_], CP](connection: PersistentConnection[F, CP])
      extends State[F, CP]
  final case object Terminating   extends State[Nothing, Nothing]
  final case object Disconnecting extends State[Nothing, Nothing]
  final case object RetryWait     extends State[Nothing, Nothing]
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
) extends ApolloClient[F, S]
    with WebSocketHandler[F, CE]
    with StreamingClientInternal[F] {
  import State._

  override def connect(): F[Unit] = {
    val warn = "connect() called while already connected or attempting to connect.".warnF

    Deferred[F, Either[Throwable, Unit]].flatMap { newLatch =>
      state.modify {
        case Disconnected          => Connecting(newLatch) -> doConnect(newLatch)
        case s @ Connecting(latch) => s                    -> (warn >> latch.get.rethrow)
        case state                 => state                -> warn
      }.flatten
    }
  }

  override def initialize(payload: F[Map[String, Json]]): F[Unit] = {
    val error =
      "initialize() called while disconnected.".raiseError

    val warn =
      "initialize() called while already initialized or attempting to initialize.".warnF

    Deferred[F, Either[Throwable, Unit]].flatMap { newLatch =>
      state.modify {
        case s @ (Disconnected | Connecting(_)) => s     -> error
        case Connected(connection)              =>
          Initializing(connection, newLatch) -> doInitialize(payload, connection, newLatch)
        case s @ Initializing(_, latch)         => s     -> (warn >> latch.get.rethrow)
        case state                              => state -> warn
      }.flatten
    }
  }

  override def terminate(): F[Unit] = ???

  override def disconnect(): F[Unit] = ???

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

  // override protected def sendMessage(msg: StreamingMessage.FromClient): F[Unit] = ???

  override protected def stopSubscription(id: ApolloClient.SubscriptionId): F[Unit] = ???

  override protected def onMessage(msg: String): F[Unit] =
    decode[StreamingMessage.FromServer](msg) match {
      case Left(e)                                          =>
        e.raiseF(s"Exception decoding WebSocket message for [$uri]")
      case Right(StreamingMessage.FromServer.ConnectionAck) =>
        state.modify {
          case Initializing(connection, latch) =>
            (Initialized(connection) -> latch.complete(().asRight))
          case s                               => s -> s"Unexpected connection_ack received from server.".warnF
        }.flatten
      case _                                                => F.unit
    }

  override protected def onError(t: Throwable): F[Unit] = ???

  override protected def onClose(event: CE): F[Unit] = ???

  private def doConnect(
    latch:   Deferred[F, Either[Throwable, Unit]],
    attempt: Int = 1
  ): F[Unit] =
    backend
      .connect(uri, onMessage, onError, onClose)
      .attempt
      .flatMap { connection =>
        state.modify {
          case s @ Connecting(_) =>
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
          case s                 =>
            s -> (latch.complete(connection.void) >>
              s"Unexpected state [$s] in connect(). Unblocking clients, but state may be inconsistent.".raiseError)
        }.flatten
      }
      .guaranteeCase {
        case ExitCase.Completed | ExitCase.Error(_) => F.unit
        case ExitCase.Canceled                      => // Attempt recovery.
          state.modify {
            case s @ Connected(_) => s -> latch.complete(Either.unit).attempt.void
            case Connecting(_)    =>
              Disconnected -> latch
                .complete("connect() was interrupted.".error[Unit])
                .attempt
                .void
            case s                =>
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
            (latch
              .complete("initialize() was interrupted. Disconnecting...".error[Unit])
              .attempt
              .void
              >> disconnect())
        case s                      =>
          s -> s"Unexpected state [$s] in canceled initialize(). Cannot recover.".raiseError
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
  ): F[ApolloClient[F, S]] = {
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
