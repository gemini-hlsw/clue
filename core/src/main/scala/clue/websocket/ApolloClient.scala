// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.effect.*
import cats.effect.implicits.*
import cats.effect.std.Queue
import cats.effect.std.SecureRandom
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import clue.*
import clue.model.GraphQLErrors
import clue.model.GraphQLQuery
import clue.model.GraphQLRequest
import clue.model.GraphQLResponse
import clue.model.StreamingMessage
import clue.model.json.given
import fs2.Stream
import fs2.concurrent.SignallingRef
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import org.typelevel.log4cats.Logger

import java.util.UUID

class ApolloClient[F[_], P, S](
  connectionParams:     P,
  reconnectionStrategy: ReconnectionStrategy,
  state:                SignallingRef[F, State[F]]
)(using
  F:                    Async[F],
  backend:              WebSocketBackend[F, P],
  logger:               Logger[F],
  secureRandom:         SecureRandom[F]
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
        s"State Modified [$oldState] ==> [$newState]".traceF >> action
      }

  // <ApolloClient>
  override def status: F[PersistentClientStatus] =
    state.get.map(_.status)

  // `changes` because a state transition does not always change the status.
  override def statusStream: fs2.Stream[F, PersistentClientStatus] =
    state.discrete.map(_.status).changes

  override def connect(): F[JsonObject] = connect(JsonObject.empty.pure[F])

  override def connect[A: Encoder.AsObject](payload: F[A]): F[JsonObject] = {
    val warn = "connect() called while already connected or attempting to connect.".warnF

    Latch[F, JsonObject].flatMap { newLatch =>
      stateModify {
        case Disconnected(connectionId, _)     =>
          Connecting(connectionId, none, payload.map(_.asJsonObject), Map.empty, newLatch) ->
            (doConnect(connectionId) >> newLatch.resolve.map(_.getOrElse(JsonObject.empty)))
        case s @ Connecting(_, _, _, _, latch) =>
          s -> (warn >> latch.resolve.map(_.getOrElse(JsonObject.empty)))
        case s                                 =>
          s -> warn.as(JsonObject.empty)
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
      case Connecting(connectionId, connection, _, subscriptions, latch) =>
        // We need a wait for the connection to establish and then disconnect it, without blocking the client.
        // The subscriptions go away with the state, so their streams must end here.
        Disconnected(connectionId.next) ->
          (latch.error(interruptedError) >>
            haltSubscriptions(subscriptions) >>
            connection
              .map(_.closeInternal(closeParameters))
              .getOrElse(F.unit)) // >> TODO wait in background to complete and close
      case Connected(connectionId, connection, _, subscriptions)         =>
        Disconnected(connectionId.next) ->
          (
            (gracefulTerminate(connection, subscriptions),
             haltSubscriptions(subscriptions)
            ).parTupled >>
              connection.closeInternal(closeParameters)
          )
      case s                                                             => s -> error
    }.uncancelable
  }

  // <StreamingClient>
  override protected[clue] def subscribeInternal[D: Decoder](
    subscription:  GraphQLQuery,
    operationName: Option[String],
    variables:     Option[JsonObject],
    extensions:    Option[JsonObject],
    descriptor:    Option[String] // This is ignored here.
  ): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
    subscriptionResource(subscription, operationName, variables, extensions)

  // <FetchClient>
  override protected[clue] def requestInternal[D: Decoder](
    document:      GraphQLQuery,
    operationName: Option[String],
    variables:     Option[JsonObject],
    extensions:    Option[JsonObject],
    modParams:     Unit => Unit,  // This is ignored here.
    descriptor:    Option[String] // This is ignored here.
  ): F[GraphQLResponse[D]] =
    // A one-shot request is a subscription that ends after its first response.
    subscriptionResource[D](document, operationName, variables, extensions)
      .use(_.head.compile.lastOrError)
  // </FetchClient>
  // </StreamingClient>
  // </ApolloClient>

  // <WebSocketHandler>
  override def onMessage(connectionId: ConnectionId, msg: String): F[Unit] =
    decode[StreamingMessage.FromServer](msg) match {
      case Left(e)                                                                 =>
        ServerMessageDecodingException(e).logAndRaiseF
      case Right(StreamingMessage.FromServer.ConnectionAck(payload))               =>
        stateModify:
          case s @ Connecting(stateConnectionId, _, _, _, latch)
              if connectionId === stateConnectionId =>
            s -> latch.release(payload)
          case s => s -> s"Unexpected connection_ack received from server.".warnF
      case Right(msg @ StreamingMessage.FromServer.Next(subscriptionId, response)) =>
        state.get.flatMap:
          case Connected(stateConnectionId, _, _, subscriptions)
              if connectionId === stateConnectionId =>
            subscriptions.get(subscriptionId) match {
              case None          =>
                s"Received data for non existant subscription id [$subscriptionId]: $response".warnF
              case Some(emitter) =>
                emitter.emitData(response)
            }
          case s @ _ =>
            UnexpectedServerMessageException[StreamingMessage.FromServer.Next, State[F]](
              msg,
              s
            ).logAndRaiseF
      case Right(msg @ StreamingMessage.FromServer.Error(subscriptionId, payload)) =>
        state.get.flatMap:
          case Connected(stateConnectionId, _, _, subscriptions)
              if connectionId === stateConnectionId =>
            subscriptions.get(subscriptionId) match {
              case None          =>
                s"Received error for non existant subscription id [$subscriptionId]: $payload".warnF
              case Some(emitter) =>
                s"Error message received for subscription id [$subscriptionId]:\n$payload".debugF >>
                  emitter.emitGraphQLErrors(payload)
            }
          case s @ _ =>
            UnexpectedServerMessageException[StreamingMessage.FromServer.Error, State[F]](
              msg,
              s
            ).logAndRaiseF
      case Right(StreamingMessage.FromServer.Complete(subscriptionId))             =>
        state.get.flatMap:
          case Connected(stateConnectionId, _, _, _) if connectionId === stateConnectionId     =>
            completeSubscription(subscriptionId)
          // Next 3 cases are expected. Server will send complete packages for subscriptions shut down when reestablishing/reinitializing.
          case Connecting(stateConnectionId, _, _, _, _) if connectionId =!= stateConnectionId =>
            F.unit
          case Connected(stateConnectionId, _, _, _) if connectionId =!= stateConnectionId     =>
            F.unit
          case s @ Disconnected(_, _)                                                          =>
            s"Complete RECEIVED for subscription [$subscriptionId] on Disconnected state.".debugF >>
              s"  \\-- State Is: [$s]".traceF
          case s @ _                                                                           =>
            s"UNEXPECTED Complete RECEIVED for subscription [$subscriptionId].".warnF >>
              s"  \\-- State Is: [$s]".traceF
      case Right(StreamingMessage.FromServer.Ping(payload))                        =>
        // Ping/Pong may be exchanged any time the socket is open, including before the
        // connection is acknowledged. Respond with a Pong whenever we have a live connection.
        state.get.flatMap:
          case Connected(_, connection, _, _) =>
            connection.send(StreamingMessage.FromClient.Pong(payload)) // Respond the same payload.
          case Connecting(_, Some(connection), _, _, _) =>
            connection.send(StreamingMessage.FromClient.Pong(payload)) // Respond the same payload.
          case _ => F.unit
      case Right(StreamingMessage.FromServer.Pong(payload))                        =>
        // A Pong is either the answer to our Ping or a unidirectional heartbeat.
        // Either way, no reply is due.
        s"Pong received from server with payload [$payload].".traceF
      case _                                                                       => s"Unexpected message received from server: [$msg]".warnF
    }

  // TODO Handle interruptions? Can callbacks be canceled?
  override def onClose(connectionId: ConnectionId, event: CloseEvent): F[Unit] = {
    val error = DisconnectedException(event.fold(_.getMessage, _.show))
    val debug = s"onClose() called with mismatching connectionId.".debugF

    s"Connection closed with event [$event].".warnF >>
      (reconnectionStrategy(0, event.asRight) match {
        case None       =>
          stateModify:
            case s @ Disconnected(_, _) =>
              s -> s"onClose() called while disconnected.".debugF
            case Connecting(stateConnectionId, _, _, subscriptions, latch)
                if connectionId === stateConnectionId =>
              // The subscriptions go away with the state, so the subscribers must be told.
              // Otherwise each subscriber stream waits forever on its queue.
              Disconnected(connectionId.next, error.some) ->
                (latch.error(error) >> crashSubscriptions(subscriptions, error))
            case Connected(stateConnectionId, _, _, subscriptions)
                if connectionId === stateConnectionId =>
              Disconnected(connectionId.next, error.some) ->
                crashSubscriptions(subscriptions, error)
            case s @ _                  =>
              s -> debug
        case Some(wait) =>
          Latch[F, JsonObject].flatMap: newLatch =>
            def waitAndConnect(nextConnectionId: ConnectionId): F[Unit] =
              "Attempting to reconnect.".warnF >>
                s"Waiting [$wait] before reconnect...".debugF >>
                timer.sleep(wait) >>
                doConnect(nextConnectionId, attempt = 1)

            stateModify:
              case s @ Disconnected(stateConnectionId, _) if connectionId === stateConnectionId =>
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
              case s @ _                                                                        =>
                s -> debug
      })
  }
  // </WebSocketHandler>

  // <ApolloClient Helpers>
  private def handleRetry(
    t:                Throwable,
    oldConnection:    Option[WebSocketConnection[F]],
    nextConnectionId: ConnectionId,
    payload:          F[JsonObject],
    subscriptions:    Map[String, Emitter[F]],
    newLatch:         Latch[F, JsonObject],
    attempt:          Int
  ): (State[F], F[Unit]) = {
    val disconnectBackend: F[Unit] =
      oldConnection.map(_.closeInternal(none).start.void).getOrElse(F.unit)

    val errorSubscriptions: F[Unit] = crashSubscriptions(subscriptions, t)

    reconnectionStrategy(attempt, t.asLeft) match
      case None       =>
        Disconnected(nextConnectionId, t.some) ->
          (errorSubscriptions >> disconnectBackend >> t.logAndRaiseF)
      case Some(wait) =>
        Connecting(nextConnectionId, none, payload, subscriptions, newLatch) ->
          (t.warnF(s"Error in connect() after attempt #[$attempt]. Retrying.") >>
            s"Waiting [$wait] before reconnect...".debugF >>
            disconnectBackend >>
            timer.sleep(wait) >>
            doConnect(nextConnectionId, attempt + 1))
  }

  private def doConnect(connectionId: ConnectionId, attempt: Int = 1): F[Unit] =
    s"Connecting. Attempt: [$attempt].".traceF >>
      backend
        .connect(connectionParams, this, connectionId)
        .attempt
        .flatMap: connectionAttempt =>
          stateModify:
            case Connecting(connectionId, None, payload, subscriptions, latch) =>
              connectionAttempt match
                case Left(t)           =>
                  handleRetry(t, none, connectionId.next, payload, subscriptions, latch, attempt)
                case Right(connection) =>
                  Connecting(connectionId, connection.some, payload, subscriptions, latch) ->
                    doInitialize(connection, payload, latch, attempt)
            case s                                                             =>
              s -> (s"Unexpected state in connect().".errorF >> s"State Is: [$s]".traceF >>
                InvalidInvocationException(
                  s"Unexpected state in connect(). Unblocking clients, but state may be inconsistent."
                ).raiseF)
        .guaranteeCase:
          case Outcome.Succeeded(_) | Outcome.Errored(_) => F.unit
          case Outcome.Canceled()                        => disconnect().start.void // Cleanup

  private def doInitialize(
    connection: WebSocketConnection[F],
    payload:    F[JsonObject],
    latch:      Latch[F, JsonObject],
    attempt:    Int
  ): F[Unit] =
    (for
      p        <- payload
      _        <- s"Initializing. Attempt: [$attempt]. Payload: [$p].".traceF
      _        <- connection.send(StreamingMessage.FromClient.ConnectionInit(p))
      result   <- latch.resolve.attempt // Sync up with server response.
      newLatch <- Latch[F, JsonObject]
      _        <- stateModify {
                    case Connecting(connectionId, Some(connection), payload, subscriptions, _) =>
                      result match
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
                    case s @ Disconnected(_, _) if result.isLeft                               =>
                      s -> (s"Disconnected while initializing.".debugF >>
                        RemoteInitializationException(result.swap.toOption.get).raiseF)
                    case s                                                                     =>
                      s -> (s"Unexpected state when initializing.".errorF >> s"State Is: [$s]".traceF >>
                        InvalidInvocationException(
                          s"Unexpected state when initializing. State may be inconsistent."
                        ).raiseF)
                  }
    yield ())
      .guaranteeCase:
        case Outcome.Succeeded(_) | Outcome.Errored(_) => F.unit
        case Outcome.Canceled()                        => disconnect().start.void // Cleanup.

  private def gracefulTerminate(
    connection:    WebSocketConnection[F],
    subscriptions: Map[String, Emitter[F]]
  ): F[Unit] =
    stopSubscriptions(connection, subscriptions).attempt.void
  // </ApolloClient Helpers>

  // <GraphQLStreamingClient Helpers>
  private def startSubscriptions(
    connection:    WebSocketConnection[F],
    subscriptions: Map[String, Emitter[F]]
  ): F[Unit] =
    subscriptions.toList
      .traverse: (id, emitter) =>
        connection.send(StreamingMessage.FromClient.Subscribe(id, emitter.request))
      .void

  // Stop = Send stop message to server. Does not halt the streams nor drop the subscriptions.
  private def stopSubscriptions(
    connection:    WebSocketConnection[F],
    subscriptions: Map[String, Emitter[F]]
  ): F[Unit] =
    subscriptions.toList.traverse { case (id, _) =>
      connection.send(StreamingMessage.FromClient.Complete(id))
    }.void

  // Halt = Terminate stream sent to client.
  private def haltSubscriptions(
    subscriptions: Map[String, Emitter[F]]
  ): F[Unit] =
    subscriptions.toList.traverse { case (_, emitter) => emitter.halt }.void

  // Crash = Terminate stream sent to client with an error.
  private def crashSubscriptions(
    subscriptions: Map[String, Emitter[F]],
    t:             Throwable
  ): F[Unit] =
    subscriptions.toList.traverse { case (_, emitter) => emitter.crash(t) }.void

  // Drop = Remove the subscription from the state, so a reconnection does not restart it, and tell
  // the server to stop sending. Never waits for a connection, since it runs uncancelably.
  private def dropSubscription(
    subscriptionId: String,
    reason:         String,
    halt:           Boolean,
    notify:         Boolean = true
  ): F[Unit] = {
    def dropped(connection: Option[WebSocketConnection[F]], emitter: Emitter[F]): F[Unit] = {
      val tellServer: F[Unit] =
        connection.traverse_(_.send(StreamingMessage.FromClient.Complete(subscriptionId)))

      // The halt runs even when the message fails. The subscription is gone from the state, so
      // nothing else can end the stream and a reader would wait forever.
      s"Dropping subscription [$subscriptionId] ($reason).".traceF >>
        tellServer.whenA(notify).guarantee(emitter.halt.whenA(halt))
    }

    def drop(
      current:       State[F],
      subscriptions: Map[String, Emitter[F]],
      connection:    Option[WebSocketConnection[F]]
    )(rebuild: Map[String, Emitter[F]] => State[F]): (State[F], F[Unit]) =
      subscriptions.get(subscriptionId) match
        case Some(emitter) =>
          rebuild(subscriptions - subscriptionId) -> dropped(connection, emitter)
        case None          =>
          current -> s"Subscription [$subscriptionId] already dropped ($reason).".traceF

    stateModify {
      case s @ Connected(cid, connection, i, subscriptions)         =>
        drop(s, subscriptions, connection.some)(Connected(cid, connection, i, _))
      case s @ Connecting(cid, connection, i, subscriptions, latch) =>
        drop(s, subscriptions, none)(Connecting(cid, connection, i, _, latch))
      // Every transition to Disconnected halts or crashes the subscriptions it drops, so the
      // stream already ended here.
      case s @ _                                                    =>
        s -> s"Subscription [$subscriptionId] not dropped ($reason). Client is disconnected.".traceF
    }
  }

  // Release = The stream ended on its own, so it needs no halt.
  private def releaseSubscription(subscriptionId: String): F[Unit] =
    dropSubscription(subscriptionId, "release", halt = false)

  // Stop = The caller ended the subscription.
  private def stopSubscription(subscriptionId: String): F[Unit] =
    dropSubscription(subscriptionId, "stop", halt = true)

  // Complete = The server ended the subscription. It needs no message back, and a reconnection
  // must not restart it.
  private def completeSubscription(subscriptionId: String): F[Unit] =
    dropSubscription(subscriptionId, "server complete", halt = true, notify = false)

  // Discard = The subscribe message never reached the server, so nobody holds or reads the
  // subscription. Without this, a reconnection would restart a subscription nobody can stop.
  private def discardSubscription(subscriptionId: String): F[Unit] =
    dropSubscription(subscriptionId, "failed subscribe", halt = false, notify = false)

  private def createSubscription[D](
    subscriptionStream: Stream[F, D],
    subscriptionId:     String
  ): GraphQLSubscription[F, D] = new GraphQLSubscription[F, D] {
    override val stream: fs2.Stream[F, D] = subscriptionStream

    override def stop(): F[Unit] = stopSubscription(subscriptionId)
  }

  private type DataQueueType[D] = Option[Either[Throwable, GraphQLResponse[D]]]

  private case class QueueEmitter[D: Decoder](
    val queue:   Queue[F, DataQueueType[D]],
    val request: GraphQLRequest[JsonObject]
  ) extends Emitter[F] {

    // A decode failure belongs to this subscription alone. Offer it to the queue instead of
    // raising it into the message handler, which would close the connection and with it every
    // other subscription. The subscriber stream rethrows queue errors, so the caller still sees it.
    def emitData(response: GraphQLResponse[Json]): F[Unit] =
      for {
        _    <- s"Emitting data:\n$response".traceF
        data <- F.delay(response.traverse(_.as[D]))
        _    <- queue.offer(data.some)
      } yield ()

    def emitGraphQLErrors(errors: GraphQLErrors): F[Unit] =
      s"Emitting error: $errors".traceF >> queue.offer(GraphQLResponse.errors(errors).asRight.some)

    def crash(t: Throwable): F[Unit] = queue.offer(t.asLeft.some)

    val halt: F[Unit] = queue.offer(none)
  }

  private def buildQueue[D: Decoder](
    request: GraphQLRequest[JsonObject]
  ): F[(String, QueueEmitter[D])] =
    for {
      queue  <- Queue.unbounded[F, DataQueueType[D]]
      uuid   <- UUIDGen.randomString[F]
      id      = s"${request.query.querySummary}-$uuid"
      emitter = QueueEmitter(queue, request)
      _      <- s"Building queue with id [$id] for query [${request.query}]}]".traceF
    } yield (id, emitter)

  // TODO Handle interruptions in subscription and query.

  // Wait until the client leaves the Connecting state.
  private def awaitConnection: F[Unit] =
    state.waitUntil(_.status =!= PersistentClientStatus.Connecting)

  private def subscriptionResource[D: Decoder](
    subscription:  GraphQLQuery,
    operationName: Option[String],
    variables:     Option[JsonObject],
    extensions:    Option[JsonObject]
  ): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
    Resource
      .makeFull[F, GraphQLSubscription[F, GraphQLResponse[D]]] { poll =>
        // Only the wait for a connection is cancelable. Once the subscription is registered, its
        // release must run, so the rest of the acquisition stays uncancelable and never waits.
        def go: F[GraphQLSubscription[F, GraphQLResponse[D]]] =
          startSubscription[D](subscription, operationName, variables, extensions)
            .flatMap(_.fold(poll(awaitConnection) >> go)(_.pure[F]))

        go
      }(_.stop().handleErrorWith(_.logF("Error stopping subscription")))
      .map(_.stream)

  // Never waits. Returns `none` if the client is still connecting, so the caller must retry.
  private def startSubscription[D: Decoder](
    subscription:  GraphQLQuery,
    operationName: Option[String],
    variables:     Option[JsonObject],
    extensions:    Option[JsonObject]
  ): F[Option[GraphQLSubscription[F, GraphQLResponse[D]]]] =
    state.get.flatMap {
      case Connected(_, _, _, _)     =>
        val request = GraphQLRequest(subscription, operationName, variables, extensions)

        buildQueue[D](request).flatMap { case (id, emitter) =>
          val stream =
            Stream
              .fromQueueUnterminated(emitter.queue)
              .evalTap(v => s"Dequeuing for subscription [$id]: [$v]".traceF)
              .unNoneTerminate
              .rethrow
              .onFinalizeCase(c =>
                s"Stream for subscription [$id] finalized with ExitCase [$c]".traceF >>
                  (c match { // If canceled, we don't want to clean up. Other fibers may be evaluating the stream. Clients can explicitly call `stop()`.
                    case Resource.ExitCase.Canceled => F.unit
                    // A failed complete message says nothing about the responses already read.
                    case _                          =>
                      releaseSubscription(id)
                        .handleErrorWith(_.logF(s"Error releasing subscription [$id]"))
                  })
              )

          // Register the subscription and send the subscribe message in one state transition, so
          // both use the same connection. If the send fails, the registration must go away again.
          s"Acquiring queue for subscription [$id]".traceF >>
            stateModify[Option[GraphQLSubscription[F, GraphQLResponse[D]]]] {
              case Connected(cid, connection, i, subscriptions) =>
                Connected(cid, connection, i, subscriptions + (id -> emitter)) ->
                  connection
                    .send(StreamingMessage.FromClient.Subscribe(id, request))
                    .onError(_ => discardSubscription(id))
                    .as(createSubscription(stream, id).some)
              case s @ Connecting(_, _, _, _, _)                =>
                s -> none.pure[F]
              case s @ _                                        =>
                s -> InvalidSubscriptionOperationException("acquire queue", id).logAndRaiseF_
            }
        }
      case Connecting(_, _, _, _, _) =>
        none.pure[F]
      // The client gave up on a connection, so the caller gets the failure that caused it.
      case Disconnected(_, cause)    =>
        cause.getOrElse(ConnectionNotInitializedException).logAndRaiseF_
    }

  // </GraphQLStreamingClient Helpers>
}

object ApolloClient {
  type SubscriptionId = UUID

  def of[F[_], P, S](
    connectionParams:     P,
    name:                 String = "",
    reconnectionStrategy: ReconnectionStrategy = ReconnectionStrategy.never
  )(using
    F:                    Async[F],
    backend:              WebSocketBackend[F, P],
    logger:               Logger[F],
    secureRandom:         SecureRandom[F]
  ): F[ApolloClient[F, P, S]] = {
    val logPrefix = s"clue.ApolloClient[${if (name.isEmpty) connectionParams else name}]"

    for {
      state <- SignallingRef[F].of[State[F]](State.Disconnected(ConnectionId.Zero))
    } yield new ApolloClient(connectionParams, reconnectionStrategy, state)(using
      F,
      backend,
      logger.withModifiedString(s => s"$logPrefix $s"),
      secureRandom
    )
  }
}
