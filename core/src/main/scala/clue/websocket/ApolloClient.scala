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
import clue.StringOps
import clue.ThrowableOps
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

  override def connect(): F[Unit] = connect(Map.empty.pure[F])

  override def connect(payload: F[Map[String, Json]]): F[Unit] = {
    val warn = "connect() called while already connected or attempting to connect.".warnF

    Latch[F].flatMap { newLatch =>
      stateModify {
        case Disconnected(connectionId)        =>
          Connecting(connectionId, none, payload, Map.empty, newLatch) ->
            doConnect(connectionId)
        case s @ Connecting(_, _, _, _, latch) =>
          s -> (warn >> latch.resolve)
        case s                                 =>
          s -> warn
      }
    }
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
      case Connecting(connectionId, connection, _, _, latch)     =>
        // We need a wait for the connection to establish and then disconnect it, without blocking the client.
        Disconnected(connectionId.next) ->
          (latch.error(interruptedError) >>
            connection
              .map(_.closeInternal(closeParameters))
              .getOrElse(F.unit)) // >> TODO wait in background to complete and close
      case Connected(connectionId, connection, _, subscriptions) =>
        Disconnected(connectionId.next) ->
          (
            (gracefulTerminate(connection, subscriptions),
             haltSubscriptions(subscriptions)
            ).parTupled >>
              connection.closeInternal(closeParameters)
          )
      case s                                                     => s -> error
    }.uncancelable
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
          case s @ Connecting(stateConnectionId, _, _, _, latch)
              if connectionId === stateConnectionId =>
            s -> latch.release
          case s => s -> s"Unexpected connection_ack received from server.".warnF
        }
      case Right(StreamingMessage.FromServer.ConnectionError(payload))             =>
        stateModify {
          case s @ Connecting(stateConnectionId, _, _, _, latch)
              if connectionId === stateConnectionId =>
            // We don't disconnect here. According to spec:
            // "It server (sic) also respond with this message in case of a parsing errors of the message (which does not disconnect the client, just ignore the message)."
            s ->
              latch.error(RemoteInitializationException(payload)).void
          case s => s -> s"Unexpected connection_error received from server.".warnF
        }
      case Right(msg @ StreamingMessage.FromServer.Data(subscriptionId, response)) =>
        state.get.flatMap {
          case Connected(stateConnectionId, _, _, subscriptions)
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
          case Connected(stateConnectionId, _, _, subscriptions)
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
          case Connected(stateConnectionId, _, _, subscriptions)
              if connectionId === stateConnectionId =>
            subscriptions.get(subscriptionId) match {
              case None               => F.unit
              case Some(subscription) => subscription.halt
            }
          // Next 3 cases are expected. Server will send complete packages for subscriptions shut down when reestablishing/reinitializing.
          case Connecting(stateConnectionId, _, _, _, _) if connectionId =!= stateConnectionId =>
            F.unit
          case Connected(stateConnectionId, _, _, _) if connectionId =!= stateConnectionId     =>
            F.unit
          case s @ Disconnected(_)                                                             =>
            s"Complete RECEIVED for subscription [$subscriptionId] on Disconnected state.".debugF >>
              s"  \\-- State Is: [$s]".traceF
          case s @ _                                                                           =>
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
          case s @ Disconnected(_)                                                         =>
            s -> s"onClose() called while disconnected.".debugF
          case Connecting(stateConnectionId, _, _, _, latch)
              if connectionId === stateConnectionId =>
            Disconnected(connectionId.next) -> latch.error(error)
          case Connected(stateConnectionId, _, _, _) if connectionId === stateConnectionId =>
            Disconnected(connectionId.next) -> F.unit
          case s @ _                                                                       =>
            s -> debug
        }
      case Some(wait) =>
        Latch[F].flatMap { newLatch =>
          def waitAndConnect(nextConnectionId: ConnectionId): F[Unit] =
            s"Connection closed with event [$event]. Attempting to reconnect.".warnF >>
              s"Waiting [$wait] before reconnect...".debugF >>
              timer.sleep(wait) >>
              doConnect(nextConnectionId, attempt = 1)

          stateModify {
            case s @ Disconnected(stateConnectionId) if connectionId === stateConnectionId =>
              s -> s"Unexpected onClose() called while disconnected. Not applying reconnectStrategy.".warnF
            case Connecting(stateConnectionId, _, initPayload, subscriptions, connectLatch)
                if connectionId === stateConnectionId =>
              Connecting(connectionId.next, none, initPayload, subscriptions, connectLatch) ->
                waitAndConnect(connectionId.next)
            case Connected(stateConnectionId, _, initPayload, subscriptions)
                if connectionId === stateConnectionId =>
              Connecting(
                connectionId.next,
                none,
                initPayload,
                subscriptions,
                newLatch
              ) -> waitAndConnect(connectionId.next)
            case s @ _                                                                     =>
              s -> debug
          }
        }
    }
  }
  // </WebSocketHandler>

  // <ApolloClient Helpers>
  private def handleRetry(
    t:                Throwable,
    oldConnection:    Option[WebSocketConnection[F]],
    nextConnectionId: ConnectionId,
    payload:          F[Map[String, Json]],
    subscriptions:    Map[String, Emitter[F]],
    newLatch:         Latch[F],
    attempt:          Int
  ): (State[F], F[Unit]) = {
    val disconnect: F[Unit] = oldConnection.map(_.closeInternal(none).start.void).getOrElse(F.unit)

    reconnectionStrategy(attempt, t.asLeft) match {
      case None       =>
        Disconnected(nextConnectionId) ->
          (disconnect >> t.logAndRaiseF)
      case Some(wait) =>
        Connecting(nextConnectionId, none, payload, subscriptions, newLatch) ->
          (t.warnF(s"Error in connect() after attempt #[$attempt]. Retrying.") >>
            s"Waiting [$wait] before reconnect...".debugF >>
            disconnect >>
            timer.sleep(wait) >>
            doConnect(nextConnectionId, attempt + 1))
    }
  }

  private def doConnect(connectionId: ConnectionId, attempt: Int = 1): F[Unit] =
    s"Connecting. Attempt: [$attempt].".traceF >>
      backend
        .connect(connectionParams, this, connectionId)
        .attempt
        .flatMap { connectionAttempt =>
          stateModify {
            case Connecting(connectionId, None, payload, subscriptions, latch) =>
              connectionAttempt match {
                case Left(t)           =>
                  handleRetry(t, none, connectionId.next, payload, subscriptions, latch, attempt)
                case Right(connection) =>
                  Connecting(connectionId, connection.some, payload, subscriptions, latch) ->
                    doInitialize(connection, payload, latch, attempt)
              }
            case s                                                             =>
              s -> (s"Unexpected state in connect().".errorF >> s"State Is: [$s]".traceF >>
                InvalidInvocationException(
                  s"Unexpected state in connect(). Unblocking clients, but state may be inconsistent."
                ).raiseF)
          }
        }
        .guaranteeCase {
          case Outcome.Succeeded(_) | Outcome.Errored(_) => F.unit
          case Outcome.Canceled()                        => disconnect().start.void // Cleanup
        }

  private def doInitialize(
    connection: WebSocketConnection[F],
    payload:    F[Map[String, Json]],
    latch:      Latch[F],
    attempt:    Int
  ): F[Unit] =
    (for {
      p        <- payload
      _        <- s"Initializing. Attempt: [$attempt]. Payload: [$p].".traceF
      _        <- connection.send(StreamingMessage.FromClient.ConnectionInit(p))
      result   <- latch.resolve.attempt // Sync up with server response.
      newLatch <- Latch[F]
      _        <- stateModify {
                    case Connecting(connectionId, Some(connection), payload, subscriptions, _) =>
                      result match {
                        case Left(t)  =>
                          handleRetry(
                            t,
                            connection.some,
                            connectionId.next,
                            payload,
                            subscriptions,
                            newLatch,
                            attempt
                          )
                        case Right(_) =>
                          Connected(connectionId, connection, payload, subscriptions) ->
                            startSubscriptions(connection, subscriptions)
                      }
                    case s                                                                     =>
                      s -> (s"Unexpected state when initializing.".errorF >> s"State Is: [$s]".traceF >>
                        InvalidInvocationException(
                          s"Unexpected state when initializing. State may be inconsistent."
                        ).raiseF)
                  }
    } yield ())
      .guaranteeCase {
        case Outcome.Succeeded(_) | Outcome.Errored(_) => F.unit
        case Outcome.Canceled()                        => disconnect().start.void // Cleanup.
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
        case Connected(_, _, _, subscriptions) =>
          for {
            _ <- s"Current subscriptions: [${subscriptions.keySet}]".traceF
            _ <- subscriptions.get(subscriptionId) match {
                   case None               =>
                     (new InvalidSubscriptionOperationException("stop", subscriptionId)).raiseF
                   case Some(subscription) => subscription.halt
                 }
          } yield ()
        case s @ _                             =>
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
      case Connected(_, _, _, _)         =>
        val request = GraphQLRequest(subscription, operationName, variables)

        buildQueue[D](request).flatMap { case (id, emitter) =>
          def acquire: F[Unit] =
            s"Acquiring queue for subscription [$id]".traceF >>
              stateModify {
                case Connected(cid, c, i, subscriptions) =>
                  Connected(cid, c, i, subscriptions + (id -> emitter)) -> F.unit
                case s @ Connecting(_, _, _, _, latch)   =>
                  s -> (latch.resolve >> acquire)
                case s @ _                               =>
                  s ->
                    InvalidSubscriptionOperationException("acquire queue", id).logAndRaiseF
              }

          def release: F[Unit] =
            s"Releasing queue for subscription[$id]".traceF >>
              stateModify {
                case Connected(cid, c, i, subscriptions) =>
                  Connected(cid, c, i, subscriptions - id) -> F.unit
                case s @ Connecting(_, _, _, _, latch)   =>
                  s -> (latch.resolve >> release)
                case s @ Disconnected(_)                 =>
                  // It's OK to call release when Disconnected.
                  // It may happen if client is disconnected and we are halting streams.
                  s -> F.unit
              }

          def sendStart: F[Unit] = state.get.flatMap {
            // The connection may have changed since we created the subscription, so we re-get it.
            case Connected(_, currentConnection, _, _) =>
              currentConnection.send(StreamingMessage.FromClient.Start(id, request))
            case Connecting(_, _, _, _, latch)         =>
              latch.resolve >> sendStart
            case s @ _                                 =>
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
      case Connecting(_, _, _, _, latch) =>
        latch.resolve >>
          startSubscription(subscription, operationName, variables, errorPolicy)
      case _                             =>
        ConnectionNotInitializedException.logAndRaiseF_
    }

  private def sendStop(subscriptionId: String): F[Unit] =
    state.get.flatMap {
      // The connection may have changed since we created the subscription, so we re-get it.
      case Connected(_, currentConnection, _, _) =>
        currentConnection.send(StreamingMessage.FromClient.Stop(subscriptionId))
      case s @ _                                 =>
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
