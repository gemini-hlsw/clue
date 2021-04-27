// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.effect._
import cats.effect.implicits._
import sttp.model.Uri
import clue.GraphQLSubscription
import io.circe._
import io.circe.parser._

import cats.syntax.all._
import java.util.UUID
import org.typelevel.log4cats.Logger
import cats.effect.std.Queue
import cats.effect.{ Ref, Temporal }
import clue.model.StreamingMessage
import clue.model.json._
import scala.concurrent.duration.FiniteDuration
import clue.model.GraphQLRequest

import fs2.Stream
import fs2.concurrent.SignallingRef

// Interface for internally handling a subscription queue.
protected[clue] trait Emitter[F[_]] {
  val request: GraphQLRequest

  def emitData(json:  Json): F[Unit]
  def emitError(json: Json): F[Unit]
  val halt: F[Unit]
}

// Client internal state for the FSM.
// We keep a connectionId throughout all states to ensure that callback events (onClose, onMessage)
// correpond to the current connection iteration. This is important in case of reconnections.
protected sealed abstract class State[+F[_], +CP](val status: PersistentClientStatus) {
  val connectionId: ConnectionId
}

protected object State {
  final case class Disconnected(connectionId: ConnectionId)
      extends State[Nothing, Nothing](PersistentClientStatus.Disconnected)
  final case class Connecting[F[_]](connectionId: ConnectionId, latch: Latch[F])
      extends State[F, Nothing](PersistentClientStatus.Connecting)
  final case class Connected[F[_], CP](
    connectionId: ConnectionId,
    connection:   PersistentConnection[F, CP]
  ) extends State[F, CP](PersistentClientStatus.Connected)
  final case class Initializing[F[_], CP](
    connectionId:  ConnectionId,
    connection:    PersistentConnection[F, CP],
    subscriptions: Map[String, Emitter[F]],
    initPayload:   Map[String, Json],
    latch:         Latch[F]
  ) extends State[F, CP](PersistentClientStatus.Initializing)
  final case class Initialized[F[_], CP](
    connectionId:  ConnectionId,
    connection:    PersistentConnection[F, CP],
    subscriptions: Map[String, Emitter[F]],
    initPayload:   Map[String, Json]
  ) extends State[F, CP](PersistentClientStatus.Initialized)
  // Reestablishing = We are in the process of reconnecting + reinitializing after a low level error/close, but we haven't connected yet.
  final case class Reestablishing[F[_]](
    connectionId:  ConnectionId,
    subscriptions: Map[String, Emitter[F]],
    initPayload:   Map[String, Json],
    connectLatch:  Latch[F],
    initLatch:     Latch[F]
  ) extends State[F, Nothing](PersistentClientStatus.Connecting)
}

class ApolloClient[F[_], S, CP, CE](
  uri:                  Uri,
  reconnectionStrategy: ReconnectionStrategy[CE],
  state:                Ref[F, State[F, CP]],
  connectionStatus:     SignallingRef[F, PersistentClientStatus]
)(implicit
  F:                    Async[F],
  backend:              PersistentBackend[F, CP, CE],
  logger:               Logger[F]
) extends PersistentStreamingClient[F, S, CP, CE]
    with PersistentBackendHandler[F, CE] {
  import State._
  val timer = Temporal[F]

  // Transition FSM state and execute an action.
  private def stateModify[A](f: State[F, CP] => (State[F, CP], F[A])): F[A] =
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

  // <ApolloClient>
  override def status: F[PersistentClientStatus] =
    connectionStatus.get

  override def statusStream: fs2.Stream[F, PersistentClientStatus] =
    connectionStatus.discrete

  override def connect(): F[Unit] = {
    val warn = "connect() called while already connected or attempting to connect.".warnF

    Latch[F].flatMap { newLatch =>
      stateModify {
        case Disconnected(connectionId)                   =>
          Connecting(connectionId, newLatch) -> doConnect(connectionId, newLatch)
        case s @ Connecting(_, latch)                     => s     -> (warn >> latch.get.rethrow)
        case s @ Reestablishing(_, _, _, connectLatch, _) =>
          s -> (warn >> connectLatch.get.rethrow)
        case state                                        => state -> warn
      }
    }
  }

  override def initialize(payload: Map[String, Json] = Map.empty): F[Unit] = {
    val error = "initialize() called while disconnected.".raiseError.void
    val warn  =
      "initialize() called while already attempting to initialize (or reestablishing).".warnF

    Latch[F].flatMap { newLatch =>
      stateModify {
        case s @ (Disconnected(_) | Connecting(_, _))                => s     -> error
        case Connected(connectionId, connection)                     =>
          Initializing(connectionId, connection, Map.empty, payload, newLatch) ->
            doInitialize(payload, connection, newLatch)
        case s @ Initializing(_, _, _, _, latch)                     => s     -> (warn >> latch.get.rethrow)
        case s @ Reestablishing(_, _, _, _, initLatch)               => s     -> (warn >> initLatch.get.rethrow)
        case Initialized(connectionId, connection, subscriptions, _) =>
          Initializing(connectionId, connection, subscriptions, payload, newLatch) ->
            (stopSubscriptions(connection, subscriptions) >>
              doInitialize(payload, connection, newLatch)).uncancelable
        case state                                                   => state -> warn
      }
    }
  }

  override def terminate(): F[Unit] = {
    val error = "terminate() called while uninitialized.".raiseError.void
    val warn  = "terminate() called while initializing.".warnF

    stateModify {
      case Initialized(connectionId, connection, subscriptions, _) =>
        Connected(connectionId, connection) ->
          (for {
            t <- gracefulTerminate(connection, subscriptions).start
            h <- haltSubscriptions(subscriptions).start
            _ <- t.join
            _ <- h.join
          } yield ())
      case s @ Initializing(_, _, _, _, latch)                     =>
        s -> (warn >> latch.get.rethrow >> terminate())
      case s @ Reestablishing(_, _, _, _, initLatch)               =>
        s -> (warn >> initLatch.get.rethrow >> terminate())
      case s                                                       =>
        s -> error
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
      case Connecting(connectionId, latch)                             =>
        // We need a wait for the connection to establish and then disconnect it, without blocking the client.
        Disconnected(connectionId.next) ->
          latch.complete(interruptedError).void // >> TODO wait in background to complete and close
      case Connected(connectionId, connection)                         =>
        Disconnected(connectionId.next) -> connection.closeInternal(closeParameters)
      case Initializing(connectionId, connection, _, _, latch)         =>
        Disconnected(connectionId.next) ->
          (latch.complete(interruptedError) >> connection.closeInternal(closeParameters))
      case Initialized(connectionId, connection, _, _)                 =>
        Disconnected(connectionId.next) -> connection.closeInternal(closeParameters)
      case Reestablishing(connectionId, _, _, connectLatch, initLatch) =>
        Disconnected(connectionId.next) ->
          (connectLatch.complete(interruptedError).attempt.void >>
            initLatch
              .complete(
                interruptedError
              )
              .void) // >> TODO wait in background to complete and close
      case s                                                           => s -> error
    }.uncancelable
  }

  override def reestablish(): F[Unit] =
    Latch[F].flatMap { newConnectLatch =>
      Latch[F].flatMap { newInitLatch =>
        stateModify {
          case s @ Reestablishing(_, _, _, _, initLatch)                         =>
            s -> (s"reestablish() called while already reestablishing.".errorF >> initLatch.get.rethrow)
          case Initialized(connectionId, connection, subscriptions, initPayload) =>
            Reestablishing(connectionId.next,
                           subscriptions,
                           initPayload,
                           newConnectLatch,
                           newInitLatch
            ) ->
              ((gracefulTerminate(connection, subscriptions) >> connection.close()).start >>
                doConnect(connectionId.next, newConnectLatch) >>
                newInitLatch.get.rethrow)
          case s @ _                                                             =>
            s -> s"reestablish() called while disconnected or uninitialized.".errorF
        }
      }
    }

  // <StreamingClient>
  override protected def subscribeInternal[D: Decoder](
    subscription:  String,
    operationName: Option[String],
    variables:     Option[Json]
  ): F[GraphQLSubscription[F, D]] =
    startSubscription(subscription, operationName, variables)(implicitly[Decoder[D]])

  // <TransactionalClient>
  override protected def requestInternal[D: Decoder](
    document:      String,
    operationName: Option[String],
    variables:     Option[Json]
  ): F[D] = F.async[D] { cb =>
    startSubscription[D](document, operationName, variables).flatMap(
      _.stream.attempt
        .evalMap(result => F.delay(cb(result)))
        .compile
        .drain
        .as(none)
    )
  }
  // </TransactionalClient>
  // </StreamingClient>
  // </ApolloClient>

  // <WebSocketHandler>
  override def onMessage(connectionId: ConnectionId, msg: String): F[Unit] =
    decode[StreamingMessage.FromServer](msg) match {
      case Left(e)                                                                   =>
        e.raiseF(s"Exception decoding message received from server: [$msg]")
      case Right(StreamingMessage.FromServer.ConnectionAck)                          =>
        stateModify {
          case Initializing(stateConnectionId, connection, subscriptions, initPayload, latch)
              if connectionId === stateConnectionId =>
            Initialized(connectionId, connection, subscriptions, initPayload) ->
              (startSubscriptions(connection, subscriptions) >> latch.complete(().asRight).void)
          case s => s -> s"Unexpected connection_ack received from server.".warnF
        }
      case Right(StreamingMessage.FromServer.ConnectionError(payload))               =>
        stateModify {
          case Initializing(stateConnectionId, connection, _, _, latch)
              if connectionId === stateConnectionId =>
            Connected(connectionId, connection) ->
              latch.complete(s"Initialization rejected by server: [$payload].".error).void
          case s => s -> s"Unexpected connection_error received from server.".warnF
        }
      case Right(StreamingMessage.FromServer.DataJson(subscriptionId, data, errors)) =>
        state.get.flatMap {
          case Initialized(stateConnectionId, _, subscriptions, _)
              if connectionId === stateConnectionId =>
            subscriptions.get(subscriptionId) match {
              case None          =>
                s"Received data for non existant subscription id [$subscriptionId]: $data".warnF
              case Some(emitter) =>
                errors.fold(emitter.emitData(data))(emitter.emitError)
            }
          case s @ _ =>
            s"UNEXPECTED Data RECEIVED for subscription [$subscriptionId]. State is: [$s]".raiseError.void
        }
      // TODO Contemplate different states.
      case Right(StreamingMessage.FromServer.Error(subscriptionId, payload))         =>
        state.get.flatMap {
          case Initialized(stateConnectionId, _, subscriptions, _)
              if connectionId === stateConnectionId =>
            subscriptions.get(subscriptionId) match {
              case None          =>
                s"Received error for non existant subscription id [$subscriptionId]: $payload".warnF
              case Some(emitter) =>
                s"Error message received for subscription id [$subscriptionId]:\n$payload".debugF >>
                  emitter.emitError(payload)
            }
          case s @ _ =>
            s"UNEXPECTED Error RECEIVED for subscription [$subscriptionId]. State is: [$s]".raiseError.void
        }
      case Right(StreamingMessage.FromServer.Complete(subscriptionId))               =>
        state.get.flatMap {
          case Initialized(stateConnectionId, _, subscriptions, _)
              if connectionId === stateConnectionId =>
            subscriptions.get(subscriptionId) match {
              case None               => F.unit
              case Some(subscription) => subscription.halt
            }
          // Next 3 cases are expected. Server will send complete packages for subscriptions shut down when reestablishing/reinitializing.
          case Reestablishing(stateConnectionId, _, _, _, _)
              if connectionId =!= stateConnectionId =>
            F.unit
          case Initializing(_, _, _, _, _)                                                   =>
            F.unit
          case Initialized(stateConnectionId, _, _, _) if connectionId =!= stateConnectionId =>
            F.unit
          case s @ _                                                                         =>
            s"UNEXPECTED Complete RECEIVED for subscription [$subscriptionId]. State is: [$s]".warnF
        }
      case Right(StreamingMessage.FromServer.ConnectionKeepAlive)                    => F.unit
      case _                                                                         => s"Unexpected message received from server: [$msg]".warnF
    }

  // TODO Handle interruptions? Can callbacks be canceled?
  override def onClose(connectionId: ConnectionId, event: CE): F[Unit] = {
    val error = (new DisconnectedException()).asLeft
    val debug = s"onClose() called with mismatching connectionId.".debugF

    reconnectionStrategy(0, event.asRight) match {
      case None       =>
        stateModify {
          case s @ Disconnected(_)                                                           =>
            s -> s"onClose() called while disconnected.".debugF
          case Connecting(stateConnectionId, latch) if connectionId === stateConnectionId    =>
            Disconnected(connectionId.next) -> latch.complete(error).void
          case Reestablishing(stateConnectionId, _, _, connectLatch, initLatch)
              if connectionId === stateConnectionId =>
            Disconnected(connectionId.next) ->
              (connectLatch.complete(error) >> initLatch.complete(error).void)
          case Initializing(stateConnectionId, _, _, _, latch)
              if connectionId === stateConnectionId =>
            Disconnected(connectionId.next) -> latch.complete(error).void
          case Connected(stateConnectionId, _) if connectionId === stateConnectionId         =>
            Disconnected(connectionId.next) -> F.unit
          case Initialized(stateConnectionId, _, _, _) if connectionId === stateConnectionId =>
            Disconnected(connectionId.next) -> F.unit
          case s @ _                                                                         =>
            s -> debug
        }
      case Some(wait) =>
        Latch[F].flatMap { newConnectLatch =>
          Latch[F].flatMap { newInitLatch =>
            def waitAndConnect(nextConnectionId: ConnectionId, latch: Latch[F]): F[Unit] =
              s"Connection closed. Attempting to reconnect.".warnF >>
                s"Waiting [$wait] before reconnect...".debugF >>
                timer.sleep(wait) >>
                doConnect(nextConnectionId, latch)

            stateModify {
              case s @ Disconnected(stateConnectionId) if connectionId === stateConnectionId =>
                s -> s"Unexpected onClose() called while disconnected. Not applying reconnectStrategy.".warnF
              case Connecting(stateConnectionId, connectLatch)
                  if connectionId === stateConnectionId =>
                Connecting(connectionId.next, connectLatch) ->
                  waitAndConnect(connectionId.next, connectLatch)
              case Connected(stateConnectionId, _) if connectionId === stateConnectionId     =>
                Connecting(connectionId.next, newConnectLatch) ->
                  waitAndConnect(connectionId.next, newConnectLatch)
              case Initializing(stateConnectionId, _, subscriptions, initPayload, latch)
                  if connectionId === stateConnectionId =>
                Reestablishing(connectionId.next,
                               subscriptions,
                               initPayload,
                               newConnectLatch,
                               latch
                ) -> waitAndConnect(connectionId.next, newConnectLatch)
              case Initialized(stateConnectionId, _, subscriptions, initPayload)
                  if connectionId === stateConnectionId =>
                Reestablishing(connectionId.next,
                               subscriptions,
                               initPayload,
                               newConnectLatch,
                               newInitLatch
                ) -> waitAndConnect(connectionId.next, newConnectLatch)
              case Reestablishing(stateConnectionId,
                                  subscriptions,
                                  initPayload,
                                  connectLatch,
                                  initLatch
                  ) if connectionId === stateConnectionId =>
                Reestablishing(connectionId.next,
                               subscriptions,
                               initPayload,
                               connectLatch,
                               initLatch
                ) -> waitAndConnect(connectionId.next, connectLatch)
              case s @ _                                                                     =>
                s -> debug
            }
          }
        }
    }
  }
  // </WebSocketHandler>

  // <ApolloClient Helpers>
  private def doConnect(
    connectionId: ConnectionId,
    latch:        Latch[F],
    attempt:      Int = 1
  ): F[Unit] =
    backend
      .connect(uri, this, connectionId)
      .attempt
      .flatMap { connection =>
        def retry(t: Throwable, wait: FiniteDuration, nextConnectionId: ConnectionId): F[Unit] =
          t.warnF(s"Error in connect() after attempt #[$attempt]. Retrying.") >>
            s"Waiting [$wait] before reconnect...".debugF >>
            timer.sleep(wait) >>
            doConnect(nextConnectionId, latch, attempt + 1)

        stateModify {
          case Connecting(connectionId, latch)                                                   =>
            connection match {
              case Left(t)  =>
                reconnectionStrategy(attempt, t.asLeft) match {
                  case None       => Disconnected(connectionId.next) -> latch.complete(t.asLeft).void
                  case Some(wait) =>
                    Connecting(connectionId.next, latch) -> retry(t, wait, connectionId.next)
                }
              case Right(c) => Connected(connectionId, c) -> latch.complete(().asRight).void
            }
          case Reestablishing(connectionId, subscriptions, initPayload, connectLatch, initLatch) =>
            connection match {
              case Left(t)  =>
                reconnectionStrategy(attempt, t.asLeft) match {
                  case None       =>
                    Disconnected(connectionId.next) ->
                      (latch.complete(t.asLeft) >> initLatch.complete(t.asLeft)).void
                  case Some(wait) =>
                    Reestablishing(connectionId.next,
                                   subscriptions,
                                   initPayload,
                                   connectLatch,
                                   initLatch
                    ) -> retry(t, wait, connectionId.next)
                }
              case Right(c) =>
                Initializing(connectionId, c, subscriptions, initPayload, initLatch) ->
                  (latch.complete(().asRight) >> doInitialize(initPayload, c, initLatch))
            }
          case s                                                                                 =>
            s -> (latch.complete(connection.void) >>
              s"Unexpected state [$s] in connect(). Unblocking clients, but state may be inconsistent.".raiseError.void)
        }
      }
      .guaranteeCase {
        case Outcome.Succeeded(_) | Outcome.Errored(_) => F.unit
        case Outcome.Canceled()                        => // Attempt recovery.
          stateModify {
            case s @ Connected(_, _)                              => s -> latch.complete(Either.unit).attempt.void
            case Connecting(connectionId, _)                      =>
              // TODO Cleanup the web socket. We should call .close() on it once it's connected. But we have to keep track of it.
              Disconnected(connectionId.next) -> latch
                .complete("connect() was canceled.".error[Unit])
                .attempt
                .void
            case Reestablishing(connectionId, _, _, _, initLatch) =>
              // TODO Cleanup the web socket. We should call .close() on it once it's connected. But we have to keep track of it.
              Disconnected(connectionId.next) ->
                (latch.complete("connect() was canceled.".error[Unit]).attempt >>
                  initLatch.complete("connect() was canceled.".error[Unit]).attempt.void)
            case s                                                =>
              s -> s"Unexpected state [$s] in canceled connect(). Cannot recover.".raiseError.void
          }
      }

  private def doInitialize(
    payload:    Map[String, Json],
    connection: PersistentConnection[F, CP],
    latch:      Latch[F]
  ): F[Unit] = (for {
    _ <- connection.send(StreamingMessage.FromClient.ConnectionInit(payload))
    _ <- latch.get.rethrow
  } yield ()).guaranteeCase {
    case Outcome.Succeeded(_) | Outcome.Errored(_) => F.unit
    case Outcome.Canceled()                        => // Attempt recovery.
      stateModify {
        case s @ Initializing(_, _, _, _, _) =>
          s ->
            (disconnect().start >> latch
              .complete("initialize() was canceled. Disconnecting...".error[Unit])
              .attempt
              .void)
        case s                               =>
          s ->
            (disconnect().start >>
              s"Unexpected state [$s] in canceled initialize(). Cannot recover. Disconnecting...".raiseError.void)
      }
  }

  private def gracefulTerminate(
    connection:    PersistentConnection[F, CP],
    subscriptions: Map[String, Emitter[F]]
  ): F[Unit] =
    (stopSubscriptions(connection, subscriptions) >>
      connection.send(StreamingMessage.FromClient.ConnectionTerminate)).attempt.void
  // </ApolloClient Helpers>

  // <GraphQLStreamingClient Helpers>
  private def startSubscriptions(
    connection:    PersistentConnection[F, CP],
    subscriptions: Map[String, Emitter[F]]
  ): F[Unit] =
    subscriptions.toList.traverse { case (id, emitter) =>
      connection.send(StreamingMessage.FromClient.Start(id, emitter.request))
    }.void

  // Stop = Send stop message to server.
  private def stopSubscriptions(
    connection:    PersistentConnection[F, CP],
    subscriptions: Map[String, Emitter[F]]
  ): F[Unit] =
    subscriptions.toList.traverse { case (id, _) =>
      connection.send(StreamingMessage.FromClient.Stop(id))
    }.void

  // Halt = Terminate stream sent to client.
  private def haltSubscriptions(
    subscriptions: Map[String, Emitter[F]]
  ): F[Unit] =
    subscriptions.toList.traverse { case (_, emitter) => emitter.halt }.void

  private def haltSubscription(subscriptionId: String): F[Unit] =
    s"Halting subscription [$subscriptionId]".debugF >>
      state.get.flatMap {
        case Initialized(_, _, subscriptions, _) =>
          for {
            _ <- s"Current subscriptions: [${subscriptions.keySet}]".traceF
            _ <- subscriptions.get(subscriptionId) match {
                   case None               => F.raiseError(new InvalidSubscriptionIdException(subscriptionId))
                   case Some(subscription) => subscription.halt
                 }
          } yield ()
        case s @ _                               =>
          s"UNEXPECTED haltSubscription for subscription [$subscriptionId]. State is: [$s]".raiseError
      }

  private def createSubscription[D](
    connection:         PersistentConnection[F, CP],
    subscriptionStream: Stream[F, D],
    subscriptionId:     String
  ): GraphQLSubscription[F, D] = new GraphQLSubscription[F, D] {
    override val stream: fs2.Stream[F, D] = subscriptionStream

    override def stop(): F[Unit] =
      for {
        _ <- haltSubscription(subscriptionId)
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
      queue.offer(data.map(_.some))
    }

    def emitError(json: Json): F[Unit] = {
      val error = new GraphQLException(json.toString)
      // TODO When an Error message is received, we terminate the stream and halt the subscription. Do we want that?
      queue.offer(error.asLeft)
    }

    val halt: F[Unit] =
      queue.offer(none.asRight)
  }

  private def buildQueue[D: Decoder](
    request: GraphQLRequest
  ): F[(String, QueueEmitter[D])] =
    for {
      queue  <- Queue.unbounded[F, Either[Throwable, Option[D]]]
      id     <- F.delay(UUID.randomUUID().toString)
      emitter = QueueEmitter(queue, request)
    } yield (id, emitter)

  // TODO Handle interruptions in subscription and query.

  private def startSubscription[D: Decoder](
    subscription:  String,
    operationName: Option[String],
    variables:     Option[Json]
  ): F[GraphQLSubscription[F, D]] =
    state.get.flatMap {
      case Initialized(_, connection, _, _)      =>
        val request = GraphQLRequest(subscription, operationName, variables)

        buildQueue[D](request).map { case (id, emitter) =>
          def acquire: F[Unit] =
            stateModify {
              case Initialized(cid, c, subscriptions, i)                          =>
                Initialized(cid, c, subscriptions + (id -> emitter), i) -> F.unit
              case s @ Initializing(_, _, _, _, latch)                            =>
                s -> (latch.get.rethrow >> acquire)
              case Reestablishing(cid, subscriptions, i, connectLatch, initLatch) =>
                Reestablishing(cid, subscriptions + (id -> emitter), i, connectLatch, initLatch) ->
                  F.unit
              case s @ _                                                          =>
                s -> s"UNEXPECTED acquire for subscription [$id]. State is: [$s]".raiseError.void
            }

          def release: F[Unit] =
            stateModify {
              case Initialized(cid, c, subscriptions, i)                          =>
                Initialized(cid, c, subscriptions - id, i) -> F.unit
              case s @ Initializing(_, _, _, _, latch)                            =>
                s -> (latch.get.rethrow >> release)
              case Reestablishing(cid, subscriptions, i, connectLatch, initLatch) =>
                Reestablishing(cid, subscriptions - id, i, connectLatch, initLatch) ->
                  F.unit
              case s @ (Connected(_, _) | Disconnected(_))                        =>
                // It's OK to call release when Connected or Disconnected.
                // It may happen if protocol was terminated or client disconnected and we are halting streams.
                s -> F.unit
              case s @ _                                                          =>
                s -> s"UNEXPECTED release for subscription [$id]. State is: [$s]".raiseError.void
            }

          def sendStart: F[Unit] = state.get.flatMap {
            // The connection may have changed since we created the subscription, so we re-get it.
            case Initialized(_, currentConnection, _, _) =>
              currentConnection.send(StreamingMessage.FromClient.Start(id, request))
            case Initializing(_, _, _, _, latch)         =>
              latch.get.rethrow >> sendStart
            case Reestablishing(_, _, _, _, initLatch)   =>
              initLatch.get.rethrow >> sendStart
            case s @ _                                   =>
              s"UNEXPECTED sendStart for subscription [$id]. State is: [$s]".raiseError.void
          }

          val bracket =
            Stream.bracket(
              s"Acquiring queue for subscription [$id]".debugF >> acquire
            )(_ => s"Releasing queue for subscription[$id]".debugF >> release)

          val stream = bracket.flatMap(_ =>
            (
              Stream.eval(sendStart) >>
                Stream
                  .fromQueueUnterminated(emitter.queue)
                  .evalTap(v => s"Dequeuing for subscription [$id]: [$v]".debugF)
            ).rethrow.unNoneTerminate
          )

          createSubscription(connection, stream, id)
        }
      case Initializing(_, _, _, _, latch)       =>
        latch.get.rethrow >> startSubscription(subscription, operationName, variables)
      case Reestablishing(_, _, _, _, initLatch) =>
        initLatch.get.rethrow >> startSubscription(subscription, operationName, variables)
      case _                                     =>
        "NOT INITIALIZED".raiseError
    }
  // </GraphQLStreamingClient Helpers>
}

object ApolloClient {
  type SubscriptionId = UUID

  def apply[F[_], S, CP, CE](
    uri:                  Uri,
    name:                 String = "",
    reconnectionStrategy: ReconnectionStrategy[CE] = ReconnectionStrategy.never
  )(implicit
    F:                    Async[F],
    backend:              PersistentBackend[F, CP, CE],
    logger:               Logger[F]
  ): F[ApolloClient[F, S, CP, CE]] = {
    val logPrefix = s"clue.ApolloClient[${if (name.isEmpty) uri else name}]"

    for {
      state            <- Ref[F].of[State[F, CP]](State.Disconnected(ConnectionId.Zero))
      connectionStatus <-
        SignallingRef[F, PersistentClientStatus](PersistentClientStatus.Disconnected)
    } yield new ApolloClient(uri, reconnectionStrategy, state, connectionStatus)(
      F,
      backend,
      logger.withModifiedString(s => s"$logPrefix $s")
    )
  }
}

object ApolloWebSocketClient {
  def of[F[_]: Async: Logger, S](
    uri:                  Uri,
    name:                 String = "",
    reconnectionStrategy: ReconnectionStrategy[WebSocketCloseEvent] = ReconnectionStrategy.never
  )(implicit backend:     WebSocketBackend[F]): F[ApolloWebSocketClient[F, S]] =
    ApolloClient[F, S, WebSocketCloseParams, WebSocketCloseEvent](uri, name, reconnectionStrategy)
}
