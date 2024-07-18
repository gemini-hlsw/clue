// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.effect.*
import cats.effect.Ref
import cats.effect.Temporal
import cats.effect.implicits.*
import cats.effect.std.Queue
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import clue.*
import clue.model.GraphQLErrors
import clue.model.GraphQLRequest
import clue.model.GraphQLResponse
import clue.model.StreamingMessage
import clue.model.json.*
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.circe.*
import io.circe.parser.*
import org.typelevel.log4cats.Logger

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

protected[clue] trait Emitter[F[_]] {
  val request: GraphQLRequest[JsonObject]

  def emitData(response: GraphQLResponse[Json]): F[Unit]
  def emitErrors(errors: GraphQLErrors): F[Unit]
  val halt: F[Unit]
}

// Client internal state for the FSM.
// We keep a connectionId throughout all states to ensure that callback events (onClose, onMessage)
// correpond to the current connection iteration. This is important in case of reconnections.
protected sealed abstract class State[F[_]](val status: PersistentClientStatus) {
  val connectionId: ConnectionId
}

protected object State {
  final case class Disconnected[F[_]](connectionId: ConnectionId)
      extends State[F](PersistentClientStatus.Disconnected)
  final case class Connecting[F[_]](connectionId: ConnectionId, latch: Latch[F])
      extends State[F](PersistentClientStatus.Connecting)
  final case class Connected[F[_]](
    connectionId: ConnectionId,
    connection:   WebSocketConnection[F]
  ) extends State[F](PersistentClientStatus.Connected)
  final case class Initializing[F[_]](
    connectionId:  ConnectionId,
    connection:    WebSocketConnection[F],
    subscriptions: Map[String, Emitter[F]],
    initPayload:   F[Map[String, Json]],
    latch:         Latch[F]
  ) extends State[F](PersistentClientStatus.Initializing)
  final case class Initialized[F[_]](
    connectionId:  ConnectionId,
    connection:    WebSocketConnection[F],
    subscriptions: Map[String, Emitter[F]],
    initPayload:   F[Map[String, Json]]
  ) extends State[F](PersistentClientStatus.Initialized)
  // Reestablishing = We are in the process of reconnecting + reinitializing after a low level error/close, but we haven't connected yet.
  final case class Reestablishing[F[_]](
    connectionId:  ConnectionId,
    subscriptions: Map[String, Emitter[F]],
    initPayload:   F[Map[String, Json]],
    connectLatch:  Latch[F],
    initLatch:     Latch[F]
  ) extends State[F](PersistentClientStatus.Connecting)
}

class ApolloClient[F[_], P, S](
  connectionParams:     P,
  reconnectionStrategy: ReconnectionStrategy,
  state:                Ref[F, State[F]],
  connectionStatus:     SignallingRef[F, PersistentClientStatus]
)(implicit
  F:                    Async[F],
  backend:              WebSocketBackend[F, P],
  logger:               Logger[F]
) extends WebSocketClient[F, S]
    with WebSocketHandler[F] {
  import State._
  val timer = Temporal[F]

  // Transition FSM state and execute an action.
  private def stateModify[A](f: State[F] => (State[F], F[A])): F[A] =
    state
      .modify { oldState =>
        val (newState, action) = f(oldState)
        newState -> ((oldState, newState, action))
      }
      .flatMap { case (oldState, newState, action) =>
        s"State Modified [$oldState] ==> [$newState]".traceF >>
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
        case s @ Connecting(_, latch)                     =>
          s -> (warn >> latch.resolve)
        case s @ Reestablishing(_, _, _, connectLatch, _) =>
          s -> (warn >> connectLatch.resolve)
        case state                                        => state -> warn
      }
    }
  }

  override def initialize(payload: F[Map[String, Json]] = F.pure(Map.empty)): F[Unit] = {
    val error = InvalidInvocationException("initialize() called while disconnected.").logAndRaiseF
    val warn  =
      "initialize() called while already attempting to initialize (or reestablishing).".warnF

    Latch[F].flatMap { newLatch =>
      stateModify {
        case s @ (Disconnected(_) | Connecting(_, _))                => s -> error
        case Connected(connectionId, connection)                     =>
          Initializing(connectionId, connection, Map.empty, payload, newLatch) ->
            doInitialize(payload, connection, newLatch)
        case s @ Initializing(_, _, _, _, latch)                     => s -> (warn >> latch.resolve)
        case s @ Reestablishing(_, _, _, _, initLatch)               => s -> (warn >> initLatch.resolve)
        case Initialized(connectionId, connection, subscriptions, _) =>
          Initializing(connectionId, connection, subscriptions, payload, newLatch) ->
            (stopSubscriptions(connection, subscriptions) >>
              doInitialize(payload, connection, newLatch)).uncancelable
      }
    }
  }

  override def terminate(): F[Unit] = {
    val error = InvalidInvocationException("terminate() called while uninitialized.").logAndRaiseF
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
        s -> (warn >> latch.resolve >> terminate())
      case s @ Reestablishing(_, _, _, _, initLatch)               =>
        s -> (warn >> initLatch.resolve >> terminate())
      case s                                                       =>
        s -> error
    }
    // .uncancelable // TODO We have waiting effects, we need to handle interruptions.
  }

  final def disconnect(closeParameters: CloseParams): F[Unit] = disconnectInternal(
    closeParameters.some
  )

  final def disconnect(): F[Unit] = disconnectInternal(none)

  private def disconnectInternal(closeParameters: Option[CloseParams]): F[Unit] = {
    val error            = InvalidInvocationException("disconnect() called while disconnected.").logAndRaiseF
    val interruptedError = InvalidInvocationException(
      "disconnect() called while connecting or initializing."
    )

    // We *could* wait for onClose to be invoked before completing, but is there a point to that?
    stateModify {
      case Connecting(connectionId, latch)                             =>
        // We need a wait for the connection to establish and then disconnect it, without blocking the client.
        Disconnected(connectionId.next) ->
          latch.error(interruptedError) // >> TODO wait in background to complete and close
      case Connected(connectionId, connection)                         =>
        Disconnected(connectionId.next) -> connection.closeInternal(closeParameters)
      case Initializing(connectionId, connection, _, _, latch)         =>
        Disconnected(connectionId.next) ->
          (latch.error(interruptedError) >> connection.closeInternal(closeParameters))
      case Initialized(connectionId, connection, _, _)                 =>
        Disconnected(connectionId.next) -> connection.closeInternal(closeParameters)
      case Reestablishing(connectionId, _, _, connectLatch, initLatch) =>
        Disconnected(connectionId.next) ->
          (connectLatch.error(interruptedError) >>
            initLatch.error(interruptedError)) // >> TODO wait in background to complete and close
      case s                                                           => s -> error
    }.uncancelable
  }

  override def reestablish(): F[Unit] =
    Latch[F].flatMap { newConnectLatch =>
      Latch[F].flatMap { newInitLatch =>
        stateModify {
          case s @ Reestablishing(_, _, _, _, initLatch)                         =>
            s -> (s"reestablish() called while already reestablishing.".errorF >> initLatch.resolve)
          case Initialized(connectionId, connection, subscriptions, initPayload) =>
            Reestablishing(
              connectionId.next,
              subscriptions,
              initPayload,
              newConnectLatch,
              newInitLatch
            ) ->
              ((gracefulTerminate(connection, subscriptions) >> connection.close()).start >>
                doConnect(connectionId.next, newConnectLatch) >>
                newInitLatch.resolve)
          case s @ _                                                             =>
            s -> s"reestablish() called while disconnected or uninitialized.".errorF
        }
      }
    }

  // <StreamingClient>
  override protected def subscribeInternal[D: Decoder, R](
    subscription:  String,
    operationName: Option[String],
    variables:     Option[JsonObject],
    errorPolicy:   ErrorPolicyProcessor[D, R]
  ): Resource[F, fs2.Stream[F, R]] =
    subscriptionResource(subscription, operationName, variables, errorPolicy)

  // <FetchClient>
  override protected def requestInternal[D: Decoder, R](
    document:      String,
    operationName: Option[String],
    variables:     Option[JsonObject],
    modParams:     Unit => Unit,
    errorPolicy:   ErrorPolicyProcessor[D, R]
  ): F[R] = F.async[R](cb =>
    startSubscription[D, R](document, operationName, variables, errorPolicy).flatMap(subscription =>
      subscription.stream.attempt
        .evalMap(result => F.delay(cb(result)))
        .compile
        .drain
        .as(none)
    )
  )
  // </FetchClient>
  // </StreamingClient>
  // </ApolloClient>

  // <WebSocketHandler>
  override def onMessage(connectionId: ConnectionId, msg: String): F[Unit] =
    decode[StreamingMessage.FromServer](msg) match {
      case Left(e)                                                                 =>
        ServerMessageDecodingException(e).logAndRaiseF
      case Right(StreamingMessage.FromServer.ConnectionAck)                        =>
        stateModify {
          case Initializing(stateConnectionId, connection, subscriptions, initPayload, latch)
              if connectionId === stateConnectionId =>
            Initialized(connectionId, connection, subscriptions, initPayload) ->
              (startSubscriptions(connection, subscriptions) >> latch.release)
          case s => s -> s"Unexpected connection_ack received from server.".warnF
        }
      case Right(StreamingMessage.FromServer.ConnectionError(payload))             =>
        stateModify {
          case Initializing(stateConnectionId, connection, _, _, latch)
              if connectionId === stateConnectionId =>
            // We don't disconnect here. According to spec:
            // "It server (sic)  also respond with this message in case of a parsing errors of the message (which does not disconnect the client, just ignore the message)."
            Connected(connectionId, connection) ->
              latch.error(RemoteInitializationException(payload)).void
          case s => s -> s"Unexpected connection_error received from server.".warnF
        }
      case Right(msg @ StreamingMessage.FromServer.Data(subscriptionId, response)) =>
        state.get.flatMap {
          case Initialized(stateConnectionId, _, subscriptions, _)
              if connectionId === stateConnectionId =>
            subscriptions.get(subscriptionId) match {
              case None          =>
                s"Received data for non existant subscription id [$subscriptionId]: $response".warnF
              case Some(emitter) =>
                emitter.emitData(response)
            }
          case s @ _ =>
            UnexpectedServerMessageException[StreamingMessage.FromServer.Data, State[F]](
              msg,
              s
            ).logAndRaiseF
        }
      // TODO Contemplate different states.
      case Right(msg @ StreamingMessage.FromServer.Error(subscriptionId, payload)) =>
        state.get.flatMap {
          case Initialized(stateConnectionId, _, subscriptions, _)
              if connectionId === stateConnectionId =>
            subscriptions.get(subscriptionId) match {
              case None          =>
                s"Received error for non existant subscription id [$subscriptionId]: $payload".warnF
              case Some(emitter) =>
                s"Error message received for subscription id [$subscriptionId]:\n$payload".debugF >>
                  emitter.emitErrors(payload)
            }
          case s @ _ =>
            UnexpectedServerMessageException[StreamingMessage.FromServer.Error, State[F]](
              msg,
              s
            ).logAndRaiseF
        }
      case Right(StreamingMessage.FromServer.Complete(subscriptionId))             =>
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
          case s @ Disconnected(_)                                                           =>
            s"Complete RECEIVED for subscription [$subscriptionId] on Disconnected state.".debugF >>
              s"  \\-- State Is: [$s]".traceF
          case s @ _                                                                         =>
            s"UNEXPECTED Complete RECEIVED for subscription [$subscriptionId].".warnF >>
              s"  \\-- State Is: [$s]".traceF
        }
      case Right(StreamingMessage.FromServer.ConnectionKeepAlive)                  => F.unit
      case _                                                                       => s"Unexpected message received from server: [$msg]".warnF
    }

  // TODO Handle interruptions? Can callbacks be canceled?
  override def onClose(connectionId: ConnectionId, event: CloseEvent): F[Unit] = {
    val error = DisconnectedException
    val debug = s"onClose() called with mismatching connectionId.".debugF

    reconnectionStrategy(0, event.asRight) match {
      case None       =>
        stateModify {
          case s @ Disconnected(_)                                                           =>
            s -> s"onClose() called while disconnected.".debugF
          case Connecting(stateConnectionId, latch) if connectionId === stateConnectionId    =>
            Disconnected(connectionId.next) -> latch.error(error)
          case Reestablishing(stateConnectionId, _, _, connectLatch, initLatch)
              if connectionId === stateConnectionId =>
            Disconnected(connectionId.next) ->
              (connectLatch.error(error) >> initLatch.error(error))
          case Initializing(stateConnectionId, _, _, _, latch)
              if connectionId === stateConnectionId =>
            Disconnected(connectionId.next) -> latch.error(error)
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
              s"Connection closed with event [$event]. Attempting to reconnect.".warnF >>
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
                Reestablishing(
                  connectionId.next,
                  subscriptions,
                  initPayload,
                  newConnectLatch,
                  latch
                ) -> waitAndConnect(connectionId.next, newConnectLatch)
              case Initialized(stateConnectionId, _, subscriptions, initPayload)
                  if connectionId === stateConnectionId =>
                Reestablishing(
                  connectionId.next,
                  subscriptions,
                  initPayload,
                  newConnectLatch,
                  newInitLatch
                ) -> waitAndConnect(connectionId.next, newConnectLatch)
              case Reestablishing(
                    stateConnectionId,
                    subscriptions,
                    initPayload,
                    connectLatch,
                    initLatch
                  ) if connectionId === stateConnectionId =>
                Reestablishing(
                  connectionId.next,
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
      .connect(connectionParams, this, connectionId)
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
                  case None       =>
                    Disconnected(connectionId.next) ->
                      (latch.error(t) >> F.raiseError(t)).void
                  case Some(wait) =>
                    Connecting(connectionId.next, latch) -> retry(t, wait, connectionId.next)
                }
              case Right(c) => Connected(connectionId, c) -> latch.release
            }
          case Reestablishing(connectionId, subscriptions, initPayload, connectLatch, initLatch) =>
            connection match {
              case Left(t)  =>
                reconnectionStrategy(attempt, t.asLeft) match {
                  case None       =>
                    Disconnected(connectionId.next) ->
                      (latch.error(t) >> initLatch.error(t) >> F.raiseError(t)).void
                  case Some(wait) =>
                    Reestablishing(
                      connectionId.next,
                      subscriptions,
                      initPayload,
                      connectLatch,
                      initLatch
                    ) -> retry(t, wait, connectionId.next)
                }
              case Right(c) =>
                Initializing(connectionId, c, subscriptions, initPayload, initLatch) ->
                  (latch.release >> doInitialize(initPayload, c, initLatch))
            }
          case s                                                                                 =>
            s -> (
              s"Unexpected state in connect(). State Is: [$s]".traceF >>
                (latch.complete(connection.void.some) >>
                  InvalidInvocationException(
                    s"Unexpected state in connect(). Unblocking clients, but state may be inconsistent."
                  ).logAndRaiseF)
            )
        }
      }
      .guaranteeCase {
        case Outcome.Succeeded(_) | Outcome.Errored(_) => F.unit
        case Outcome.Canceled()                        => // Cleanup.
          stateModify {
            case s @ Connected(_, _)                              => s -> latch.release
            case Connecting(connectionId, _)                      =>
              // TODO Cleanup the web socket. We should call .close() on it once it's connected. But we have to keep track of it.
              Disconnected(connectionId.next) -> latch.cancel
            case Reestablishing(connectionId, _, _, _, initLatch) =>
              // TODO Cleanup the web socket. We should call .close() on it once it's connected. But we have to keep track of it.
              Disconnected(connectionId.next) ->
                (latch.cancel >> initLatch.cancel)
            case s                                                =>
              s -> UnexpectedInternalStateException("cancelling connect()", s).logAndRaiseF
          }
      }

  private def doInitialize(
    payload:    F[Map[String, Json]],
    connection: WebSocketConnection[F],
    latch:      Latch[F]
  ): F[Unit] = (for {
    _ <- payload >>= (p => connection.send(StreamingMessage.FromClient.ConnectionInit(p)))
    _ <- latch.resolve
  } yield ()).guaranteeCase {
    case Outcome.Succeeded(_) | Outcome.Errored(_) => F.unit
    case Outcome.Canceled()                        => // Cleanup.
      stateModify {
        case s @ Initializing(_, _, _, _, _) =>
          s ->
            (disconnect().start >> latch.cancel)
        case s                               =>
          s ->
            (disconnect().start >>
              UnexpectedInternalStateException("cancelling initialize()", s).logAndRaiseF)
      }
  }

  private def gracefulTerminate(
    connection:    WebSocketConnection[F],
    subscriptions: Map[String, Emitter[F]]
  ): F[Unit] =
    (stopSubscriptions(connection, subscriptions) >>
      connection.send(StreamingMessage.FromClient.ConnectionTerminate)).attempt.void
  // </ApolloClient Helpers>

  // <GraphQLStreamingClient Helpers>
  private def startSubscriptions(
    connection:    WebSocketConnection[F],
    subscriptions: Map[String, Emitter[F]]
  ): F[Unit] =
    subscriptions.toList.traverse { case (id, emitter) =>
      connection.send(StreamingMessage.FromClient.Start(id, emitter.request))
    }.void

  // Stop = Send stop message to server.
  private def stopSubscriptions(
    connection:    WebSocketConnection[F],
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
                   case None               =>
                     F.raiseError(new InvalidSubscriptionOperationException("stop", subscriptionId))
                   case Some(subscription) => subscription.halt
                 }
          } yield ()
        case s @ _                               =>
          InvalidSubscriptionOperationException("stop", subscriptionId).logAndRaiseF
      }

  private def createSubscription[D](
    subscriptionStream: Stream[F, D],
    subscriptionId:     String
  ): GraphQLSubscription[F, D] = new GraphQLSubscription[F, D] {
    override val stream: fs2.Stream[F, D] = subscriptionStream

    override def stop(): F[Unit] =
      for {
        _ <- haltSubscription(subscriptionId)
        _ <- sendStop(subscriptionId)
      } yield ()
  }

  private type DataQueueType[D] = Option[GraphQLResponse[D]]

  private case class QueueEmitter[D: Decoder](
    val queue:   Queue[F, DataQueueType[D]],
    val request: GraphQLRequest[JsonObject]
  ) extends Emitter[F] {

    def emitData(response: GraphQLResponse[Json]): F[Unit] =
      for {
        _    <- s"Emitting data:\n$response".traceF
        data <- F.delay(response.traverse(_.as[D])).rethrow
        _    <- queue.offer(data.some)
      } yield ()

    def emitErrors(errors: GraphQLErrors): F[Unit] =
      s"Emitting error: $errors".traceF >> queue.offer(GraphQLResponse.errors(errors).some)

    val halt: F[Unit] = queue.offer(none)
  }

  private def buildQueue[D: Decoder](
    request: GraphQLRequest[JsonObject]
  ): F[(String, QueueEmitter[D])] =
    for {
      queue  <- Queue.unbounded[F, DataQueueType[D]]
      id     <- UUIDGen.randomString[F]
      emitter = QueueEmitter(queue, request)
      _      <- s"Building queue with id [$id] for query [${request.query}]}]".traceF
    } yield (id, emitter)

  // TODO Handle interruptions in subscription and query.

  private def subscriptionResource[D: Decoder, R](
    subscription:  String,
    operationName: Option[String],
    variables:     Option[JsonObject],
    errorPolicy:   ErrorPolicyProcessor[D, R]
  ): Resource[F, fs2.Stream[F, R]] =
    Resource
      .make(startSubscription[D, R](subscription, operationName, variables, errorPolicy))(
        _.stop()
          .handleErrorWith(_.logF("Error stopping subscription"))
      )
      .map(_.stream)

  private def startSubscription[D: Decoder, R](
    subscription:  String,
    operationName: Option[String],
    variables:     Option[JsonObject],
    errorPolicy:   ErrorPolicyProcessor[D, R]
  ): F[GraphQLSubscription[F, R]] =
    state.get.flatMap {
      case Initialized(_, _, _, _)               =>
        val request = GraphQLRequest(subscription, operationName, variables)

        buildQueue[D](request).flatMap { case (id, emitter) =>
          def acquire: F[Unit] =
            s"Acquiring queue for subscription [$id]".traceF >>
              stateModify {
                case Initialized(cid, c, subscriptions, i)                          =>
                  Initialized(cid, c, subscriptions + (id -> emitter), i) -> F.unit
                case s @ Initializing(_, _, _, _, latch)                            =>
                  s -> (latch.resolve >> acquire)
                case Reestablishing(cid, subscriptions, i, connectLatch, initLatch) =>
                  Reestablishing(
                    cid,
                    subscriptions + (id -> emitter),
                    i,
                    connectLatch,
                    initLatch
                  ) ->
                    F.unit
                case s @ _                                                          =>
                  s ->
                    InvalidSubscriptionOperationException("acquire queue", id).logAndRaiseF
              }

          def release: F[Unit] =
            s"Releasing queue for subscription[$id]".traceF >>
              stateModify {
                case Initialized(cid, c, subscriptions, i)                          =>
                  Initialized(cid, c, subscriptions - id, i) -> F.unit
                case s @ Initializing(_, _, _, _, latch)                            =>
                  s -> (latch.resolve >> release)
                case Reestablishing(cid, subscriptions, i, connectLatch, initLatch) =>
                  Reestablishing(cid, subscriptions - id, i, connectLatch, initLatch) ->
                    F.unit
                case s @ (Connected(_, _) | Disconnected(_))                        =>
                  // It's OK to call release when Connected or Disconnected.
                  // It may happen if protocol was terminated or client disconnected and we are halting streams.
                  s -> F.unit
                case s @ _                                                          =>
                  s ->
                    InvalidSubscriptionOperationException("release queue", id).logAndRaiseF
              }

          def sendStart: F[Unit] = state.get.flatMap {
            // The connection may have changed since we created the subscription, so we re-get it.
            case Initialized(_, currentConnection, _, _) =>
              currentConnection.send(StreamingMessage.FromClient.Start(id, request))
            case Initializing(_, _, _, _, latch)         =>
              latch.resolve >> sendStart
            case Reestablishing(_, _, _, _, initLatch)   =>
              initLatch.resolve >> sendStart
            case s @ _                                   =>
              InvalidSubscriptionOperationException("send start", id).logAndRaiseF
          }

          val stream =
            Stream
              .fromQueueUnterminated(emitter.queue)
              .evalTap(v => s"Dequeuing for subscription [$id]: [$v]".traceF)
              .unNoneTerminate
              .evalMap(errorPolicy.process(_))
              .onFinalizeCase(c =>
                s"Stream for subscription [$id] finalized with ExitCase [$c]".traceF >>
                  (c match { // If canceled, we don't want to clean up. Other fibers may be evaluating the stream. Clients can explicitly call `stop()`.
                    case Resource.ExitCase.Canceled => F.unit
                    case _                          => release
                  })
              )

          (acquire >> sendStart).as(createSubscription(stream, id))
        }
      case Initializing(_, _, _, _, latch)       =>
        latch.resolve >>
          startSubscription(subscription, operationName, variables, errorPolicy)
      case Reestablishing(_, _, _, _, initLatch) =>
        initLatch.resolve >>
          startSubscription(subscription, operationName, variables, errorPolicy)
      case _                                     =>
        ConnectionNotInitializedException.logAndRaiseF_
    }

  private def sendStop(subscriptionId: String): F[Unit] =
    state.get.flatMap {
      // The connection may have changed since we created the subscription, so we re-get it.
      case Initialized(_, currentConnection, _, _) =>
        currentConnection.send(StreamingMessage.FromClient.Stop(subscriptionId))
      case s @ _                                   =>
        InvalidSubscriptionOperationException("send stop", subscriptionId).logAndRaiseF
    }

  // </GraphQLStreamingClient Helpers>
}

object ApolloClient {
  type SubscriptionId = UUID

  def of[F[_], P, S](
    connectionParams:     P,
    name:                 String = "",
    reconnectionStrategy: ReconnectionStrategy = ReconnectionStrategy.never
  )(implicit
    F:                    Async[F],
    backend:              WebSocketBackend[F, P],
    logger:               Logger[F]
  ): F[ApolloClient[F, P, S]] = {
    val logPrefix = s"clue.ApolloClient[${if (name.isEmpty) connectionParams else name}]"

    for {
      state            <- Ref[F].of[State[F]](State.Disconnected(ConnectionId.Zero))
      connectionStatus <-
        SignallingRef[F, PersistentClientStatus](PersistentClientStatus.Disconnected)
    } yield new ApolloClient(connectionParams, reconnectionStrategy, state, connectionStatus)(
      F,
      backend,
      logger.withModifiedString(s => s"$logPrefix $s")
    )
  }
}
