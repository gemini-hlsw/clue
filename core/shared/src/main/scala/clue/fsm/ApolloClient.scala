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
import cats.effect.Sync
import cats.effect.concurrent.Deferred
import java.util.UUID
import cats.effect.ConcurrentEffect
import io.chrisdavenport.log4cats.Logger
import cats.effect.ExitCase
import cats.effect.Timer
import clue.model.StreamingMessage
import clue.model.json._
import scala.concurrent.duration.FiniteDuration
import clue.model.GraphQLRequest

import fs2.Stream
import fs2.concurrent.Queue
import fs2.concurrent.SignallingRef

protected[clue] trait Emitter[F[_]] {
  val request: GraphQLRequest

  def emitData(json:  Json): F[Unit]
  def emitError(json: Json): F[Unit]
  def terminate(): F[Unit]
}

// Client facing interface
trait ApolloClient[F[_], S, CP] extends GraphQLStreamingClient[F, S] {
  def connect(): F[Unit]
  def initialize(payload: F[Map[String, Json]]): F[Unit]

  final def initialize(payload: Map[String, Json] = Map.empty)(implicit sync: Sync[F]): F[Unit] =
    initialize(sync.delay(payload))

  def terminate(): F[Unit]
  def disconnect(closeParameters: CP): F[Unit]
  def disconnect(): F[Unit]

  def status: F[StreamingClientStatus]
  def statusStream: fs2.Stream[F, StreamingClientStatus]
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

// TODO: Reestablish subscriptions

protected sealed abstract class State[+F[_], +CP](val status: StreamingClientStatus)
protected object State {
  final case object Disconnected extends State[Nothing, Nothing](StreamingClientStatus.Disconnected)
  final case class Connecting[F[_]](latch: Deferred[F, Either[Throwable, Unit]])
      extends State[F, Nothing](StreamingClientStatus.Connecting)
  final case class Connected[F[_], CP](connection: PersistentConnection[F, CP])
      extends State[F, CP](StreamingClientStatus.Connected)
  final case class Initializing[F[_], CP](
    initPayloadF: F[Map[String, Json]],
    connection:   PersistentConnection[F, CP],
    latch:        Deferred[F, Either[Throwable, Unit]]
  )                              extends State[F, CP](StreamingClientStatus.Initializing)
  final case class Initialized[F[_], CP](
    initPayloadF:  F[Map[String, Json]],
    connection:    PersistentConnection[F, CP],
    subscriptions: Map[String, Emitter[F]] = Map.empty
  )                              extends State[F, CP](StreamingClientStatus.Initialized)
  // TODO Propagate subscriptions across reconnections
  // Reestablishing = We are in the process of reconnecting + reinitializing after a low level error/close, but we haven't connected yet.
  final case class Reestablishing[F[_]](
    initPayloadF: F[Map[String, Json]],
    connectLatch: Deferred[F, Either[Throwable, Unit]],
    initLatch:    Deferred[F, Either[Throwable, Unit]]
  )                              extends State[F, Nothing](StreamingClientStatus.Connecting)

}

class ApolloClientImpl[F[_], S, CP, CE](
  uri:                  Uri,
  reconnectionStrategy: ReconnectionStrategy[CE],
  state:                Ref[F, State[F, CP]],
  connectionStatus:     SignallingRef[F, StreamingClientStatus]
)(implicit
  F:                    ConcurrentEffect[F],
  backend:              PersistentBackend[F, CP, CE],
  timer:                Timer[F],
  logger:               Logger[F]
) extends ApolloClient[F, S, CP]
    with WebSocketHandler[F, CE]
    with StreamingClientInternal[F] {
  import State._

  private def stateModify(f: State[F, CP] => (State[F, CP], F[Unit])): F[Unit] =
    state
      .modify { oldState =>
        val (newState, action) = f(oldState)
        newState -> ((oldState, newState, action))
      }
      .flatMap { case (oldState, newState, action) =>
        s"State Modified [$oldState] ==> [$newState]".debugF >>
          connectionStatus.set(newState.status) >>
          action
      }

  override def status: F[StreamingClientStatus] =
    connectionStatus.get

  override def statusStream: fs2.Stream[F, StreamingClientStatus] =
    connectionStatus.discrete

  override def connect(): F[Unit] = {
    val warn = "connect() called while already connected or attempting to connect.".warnF

    Deferred[F, Either[Throwable, Unit]].flatMap { newLatch =>
      stateModify {
        case Disconnected                           => Connecting(newLatch) -> doConnect(newLatch)
        case s @ Connecting(latch)                  => s                    -> (warn >> latch.get.rethrow)
        case s @ Reestablishing(_, connectLatch, _) => s                    -> (warn >> connectLatch.get.rethrow)
        case state                                  => state                -> warn
      }
    }
  }

  override def initialize(payloadF: F[Map[String, Json]]): F[Unit] = {
    val error = "initialize() called while disconnected.".raiseError.void
    val warn  = "initialize() called while already initialized or attempting to initialize.".warnF

    Deferred[F, Either[Throwable, Unit]].flatMap { newLatch =>
      stateModify {
        case s @ (Disconnected | Connecting(_))  => s     -> error
        case Connected(connection)               =>
          Initializing(payloadF, connection, newLatch) -> doInitialize(payloadF,
                                                                       connection,
                                                                       newLatch
          )
        case s @ Initializing(_, _, latch)       => s     -> (warn >> latch.get.rethrow)
        case s @ Reestablishing(_, _, initLatch) => s     -> (warn >> initLatch.get.rethrow)
        case state                               => state -> warn
      }
    }
  }

  override def terminate(): F[Unit] = {
    val error = "terminate() called while uninitialized.".raiseError.void
    val warn  = "terminate() called while initializing.".warnF

    stateModify {
      case Initialized(_,
                       connection,
                       _
          ) => // TODO: Terminate subscriptions and gracefully unsubscribe.
        Connected(connection) -> connection.send(StreamingMessage.FromClient.ConnectionTerminate)
      case s @ Initializing(_, _, latch)       => s -> (warn >> latch.get.rethrow >> terminate())
      case s @ Reestablishing(_, _, initLatch) =>
        s -> (warn >> initLatch.get.rethrow >> terminate())
      case s                                   => s -> error
    }
    // .uncancelable // TODO We have waiting effects, we need to handle interruptions.
  }

  final def disconnect(closeParameters: CP): F[Unit] = disconnectInternal(closeParameters.some)

  final def disconnect(): F[Unit] = disconnectInternal(none)

  private def disconnectInternal(closeParameters: Option[CP]): F[Unit] = {
    val error            = "disconnect() called while disconnected.".raiseError.void
    val interruptedError = "disconnect() called while connecting or initializing.".error

    // We *could* wait for onClose to be invoked before completing, but is there a point to that?

    stateModify {
      case Connecting(latch)                          =>
        // We need a wait for the connection to establish and then disconnect it, without blocking the client.
        Disconnected -> latch.complete(
          interruptedError
        ) // >> TODO wait in background to complete and close
      case Connected(connection)                      => Disconnected -> connection.closeInternal(closeParameters)
      case Initializing(_, connection, latch)         =>
        Disconnected -> (latch.complete(interruptedError) >>
          connection.closeInternal(closeParameters))
      case Initialized(_, connection, _)              =>
        Disconnected -> connection.closeInternal(closeParameters)
      case Reestablishing(_, connectLatch, initLatch) =>
        Disconnected -> (connectLatch.complete(interruptedError).attempt.void >>
          initLatch.complete(
            interruptedError
          )) // >> TODO wait in background to complete and close
      case s                                          => s            -> error
    }.uncancelable
  }

  override protected def subscribeInternal[D: Decoder](
    subscription:  String,
    operationName: Option[String],
    variables:     Option[Json]
  ): F[GraphQLSubscription[F, D]] =
    startSubscription(subscription, operationName, variables)(implicitly[Decoder[D]])
      .map(_.subscription)

  override protected def requestInternal[D: Decoder](
    document:      String,
    operationName: Option[String],
    variables:     Option[Json]
  ): F[D] = F.asyncF[D] { cb =>
    startSubscription[D](document, operationName, variables).flatMap { subscriptionInfo =>
      subscriptionInfo.subscription.stream.attempt
        .evalMap(result => F.delay(cb(result)) >> subscriptionInfo.onComplete)
        .compile
        .drain
    }
  }

  override protected def stopSubscription(id: ApolloClient.SubscriptionId): F[Unit] = ???

  override protected def onMessage(msg: String): F[Unit] =
    decode[StreamingMessage.FromServer](msg) match {
      case Left(e)                                                       =>
        e.raiseF(s"Exception decoding message received from server: [$msg]")
      case Right(StreamingMessage.FromServer.ConnectionAck)              =>
        stateModify {
          case Initializing(initPayloadF, connection, latch) =>
            (Initialized(initPayloadF, connection, Map.empty) -> latch.complete(().asRight))
          case s                                             => s -> s"Unexpected connection_ack received from server.".warnF
        }
      case Right(StreamingMessage.FromServer.ConnectionError(payload))   =>
        stateModify {
          case Initializing(_, connection, latch) =>
            (Connected(connection) -> latch.complete(
              s"Initialization rejected by server: [$payload].".error
            ))
          case s                                  => s -> s"Unexpected connection_error received from server.".warnF
        }
      case Right(StreamingMessage.FromServer.DataJson(id, data, errors)) =>
        state.get.flatMap {
          case Initialized(_, _, subscriptions) =>
            subscriptions.get(id) match {
              case None          =>
                s"Received data for non existant subscription id [$id]: $data".errorF
              case Some(emitter) =>
                errors.fold(emitter.emitData(data))(emitter.emitError)
            }
          case _                                => "UNEXPECTED!".raiseError.void
        }
      case Right(StreamingMessage.FromServer.Error(id @ _, payload @ _)) => F.unit
      case Right(StreamingMessage.FromServer.Complete(id @ _))           => F.unit
      case _                                                             => F.unit
    }

  // TODO Handle interruptions? Can callbacks be canceled?
  override protected def onClose(event: CE): F[Unit] = {
    val warn =
      "onClose() called while connecting. This is unexpected. Client state may be incosistent.".warnF

    reconnectionStrategy(0, event.asRight) match {
      case None       =>
        stateModify {
          case s @ Disconnected                              => s            -> s"onClose() called while disconnected.".warnF
          case s @ (Connecting(_) | Reestablishing(_, _, _)) => s            -> warn
          case Initializing(_, _, latch)                     =>
            Disconnected -> latch.complete((new DisconnectedException()).asLeft)
          case _                                             => Disconnected -> F.unit
        }
      case Some(wait) =>
        Deferred[F, Either[Throwable, Unit]].flatMap { newConnectLatch =>
          Deferred[F, Either[Throwable, Unit]].flatMap { newInitLatch =>
            val waitF = s"Connection closed. Attempting to reconnect...".warnF >> timer.sleep(wait)

            stateModify {
              // TODO This logic is wrong. onClose can be called at any time, it is never unexpected.
              // Also: if disconnect was called and we want reconnection logic to kick in, that is not happening,
              // since disconnect sets state to disconnected!
              // Maybe we can have a reconnect() method??
              case s @ Disconnected                              =>
                s -> s"onClose() called while disconnected. Since this is unexpected, reconnection strategy will not be applied.".warnF
              case s @ (Connecting(_) | Reestablishing(_, _, _)) => s -> warn
              case Connected(_)                                  =>
                Connecting(newConnectLatch) -> (waitF >> doConnect(newConnectLatch))
              case Initializing(initPayloadF, _, latch)          =>
                Reestablishing(initPayloadF, newConnectLatch, latch) ->
                  (waitF >> doConnect(newConnectLatch))
              case Initialized(initPayloadF, _, _)               =>
                Reestablishing(initPayloadF, newConnectLatch, newInitLatch) ->
                  (waitF >> doConnect(newConnectLatch))
            }
          }
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
        def retry(t: Throwable, wait: FiniteDuration): F[Unit] =
          t.warnF(s"Error in connect() after attempt #[$attempt]. Retrying...") >>
            timer.sleep(wait) >>
            doConnect(latch, attempt + 1)

        stateModify {
          case s @ Connecting(_)                              =>
            connection match {
              case Left(t)  =>
                reconnectionStrategy(attempt, t.asLeft) match {
                  case None       => Disconnected -> latch.complete(t.asLeft)
                  case Some(wait) => s            -> retry(t, wait)
                }
              case Right(c) => Connected(c) -> latch.complete(().asRight)
            }
          case s @ Reestablishing(initPayloadF, _, initLatch) =>
            connection match {
              case Left(t)  =>
                reconnectionStrategy(attempt, t.asLeft) match {
                  case None       =>
                    Disconnected -> (latch.complete(t.asLeft) >> initLatch.complete(t.asLeft))
                  case Some(wait) => s -> retry(t, wait)
                }
              case Right(c) =>
                Initializing(initPayloadF, c, initLatch) -> (latch.complete(().asRight) >>
                  doInitialize(initPayloadF, c, initLatch))
            }
          case s                                              =>
            s -> (latch.complete(connection.void) >>
              s"Unexpected state [$s] in connect(). Unblocking clients, but state may be inconsistent.".raiseError.void)
        }
      }
      .guaranteeCase {
        case ExitCase.Completed | ExitCase.Error(_) => F.unit
        case ExitCase.Canceled                      => // Attempt recovery.
          stateModify {
            case s @ Connected(_)                => s -> latch.complete(Either.unit).attempt.void
            case Connecting(_)                   =>
              // TODO Cleanup the web socket. We should call .close() on it once it's connected. But we have to keep track of it.
              Disconnected -> latch
                .complete("connect() was canceled.".error[Unit])
                .attempt
                .void
            case Reestablishing(_, _, initLatch) =>
              // TODO Cleanup the web socket. We should call .close() on it once it's connected. But we have to keep track of it.
              Disconnected ->
                (latch.complete("connect() was canceled.".error[Unit]).attempt >>
                  initLatch.complete("connect() was canceled.".error[Unit]).attempt.void)
            case s                               =>
              s -> s"Unexpected state [$s] in canceled connect(). Cannot recover.".raiseError.void
          }
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
      stateModify {
        case s @ Initializing(_, _, _) =>
          s ->
            (disconnect().start >> latch
              .complete("initialize() was canceled. Disconnecting...".error[Unit])
              .attempt
              .void)
        case s                         =>
          s -> (disconnect().start >> s"Unexpected state [$s] in canceled initialize(). Cannot recover. Disconnecting...".raiseError.void)
      }
  }

  private def terminateSubscription(id: String, lenient: Boolean = false): F[Unit] =
    s"Terminating subscription [$id]".debugF >>
      state.get.flatMap {
        case Initialized(_, _, subscriptions) =>
          for {
            _ <- s"Current subscriptions: [${subscriptions.keySet}]".debugF
            _ <- subscriptions
                   .get(id)
                   .fold[F[Unit]](
                     if (lenient) F.unit
                     else F.raiseError(new InvalidSubscriptionIdException(id))
                   )(_.terminate())
          } yield ()
        case _                                => "UNEXPECTED!".raiseError
      }

  private def createSubscription[D](
    connection:         PersistentConnection[F, CP],
    subscriptionStream: Stream[F, D],
    subscriptionId:     String
  ): GraphQLSubscription[F, D] = new GraphQLSubscription[F, D] {
    override val stream: fs2.Stream[F, D] = subscriptionStream

    override def stop(): F[Unit] =
      for {
        _ <- terminateSubscription(subscriptionId)
        _ <- connection.send(StreamingMessage.FromClient.Stop(subscriptionId))
      } yield ()
  }

  private type DataQueue[D] = Queue[F, Either[Throwable, Option[D]]]

  private case class QueueEmitter[D: Decoder](
    val queue:   DataQueue[D],
    val request: GraphQLRequest
  ) extends Emitter[F] {

    def emitData(json: Json): F[Unit] = {
      val data = json.as[D]
      queue.enqueue1(data.map(_.some))
    }

    def emitError(json: Json): F[Unit] = {
      val error = new GraphQLException(json.toString)
      queue.enqueue1(Left(error))
    }

    def terminate(): F[Unit] =
      queue.enqueue1(Right(None))
  }

  private def buildQueue[D: Decoder](
    request: GraphQLRequest
  ): F[(String, QueueEmitter[D])] =
    for {
      queue  <- Queue.unbounded[F, Either[Throwable, Option[D]]]
      id     <- F.delay(UUID.randomUUID().toString)
      emitter = QueueEmitter(queue, request)
    } yield (id, emitter)

  private case class SubscriptionInfo[D](
    subscription: GraphQLSubscription[F, D],
    onComplete:   F[Unit]
  )

  // TODO Handle interruptions in subscription and query.

  private def startSubscription[D: Decoder](
    subscription:  String,
    operationName: Option[String],
    variables:     Option[Json]
  ): F[SubscriptionInfo[D]] =
    state.get.flatMap {
      case Initialized(_, connection, _)   =>
        val request = GraphQLRequest(subscription, operationName, variables)

        buildQueue[D](request).map { case (id, emitter) =>
          def acquire: F[Unit] =
            stateModify {
              case Initialized(i, c, subscriptions)    =>
                Initialized(i, c, subscriptions + (id -> emitter)) -> F.unit
              case s @ Initializing(_, _, latch)       =>
                s -> (latch.get.rethrow >> acquire)
              case s @ Reestablishing(_, _, initLatch) =>
                s -> (initLatch.get.rethrow >> acquire)
              case s @ _                               => s -> "UNEXPECTED!".raiseError.void
            }

          def release: F[Unit] =
            stateModify {
              case Initialized(i, c, subscriptions)    =>
                Initialized(i, c, subscriptions - id) -> F.unit
              case s @ Initializing(_, _, latch)       =>
                s -> (latch.get.rethrow >> release)
              case s @ Reestablishing(_, _, initLatch) =>
                s -> (initLatch.get.rethrow >> release)
              case s @ _                               => s -> "UNEXPECTED!".raiseError.void
            }

          def sendStart: F[Unit] = state.get.flatMap {
            // The connection may have changed since we created the subscription, so we re-get it.
            case Initialized(_, currentConnection, _) =>
              currentConnection.send(StreamingMessage.FromClient.Start(id, request))
            case Initializing(_, _, latch)            =>
              latch.get.rethrow >> sendStart
            case Reestablishing(_, _, initLatch)      =>
              initLatch.get.rethrow >> sendStart
            case _                                    => "UNEXPECTED!".raiseError.void
          }

          val bracket =
            Stream.bracket(
              s"Acquiring queue for subscription [$id]".debugF >> acquire
            )(_ => s"Releasing queue for subscription[$id]".debugF >> release)

          val stream = bracket.flatMap(_ =>
            (
              Stream.eval(sendStart) >>
                emitter.queue.dequeue
                  .evalTap(v => s"Dequeuing for subscription [$id]: [$v]".debugF)
            ).rethrow.unNoneTerminate
              .onError { case t: Throwable =>
                Stream.eval(t.logF(s"Error in subscription [$id]: "))
              }
          )

          SubscriptionInfo(createSubscription(connection, stream, id), terminateSubscription(id))
        }
      case Initializing(_, _, latch)       =>
        latch.get.rethrow >> startSubscription(subscription, operationName, variables)
      case Reestablishing(_, _, initLatch) =>
        initLatch.get.rethrow >> startSubscription(subscription, operationName, variables)
      case _                               => "NOT INITIALIZED".raiseError
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
      state            <- Ref[F].of[State[F, CP]](State.Disconnected)
      connectionStatus <-
        SignallingRef[F, StreamingClientStatus](StreamingClientStatus.Disconnected)
    } yield new ApolloClientImpl(uri, reconnectionStrategy, state, connectionStatus)(
      F,
      backend,
      timer,
      logger.withModifiedString(s => s"$logPrefix $s")
    )
  }
}
