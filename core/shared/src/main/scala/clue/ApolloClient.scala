// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import java.util.UUID

import cats.effect._
import cats.effect.concurrent._
import cats.syntax.all._
import clue.model._
import clue.model.json._
import fs2.Stream
import fs2.concurrent.Queue
import fs2.concurrent.SignallingRef
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.parser._
import sttp.model.Uri
import scala.concurrent.duration.Duration

protected[clue] trait Emitter[F[_]] {
  val request: GraphQLRequest

  def emitData(json:  Json): F[Unit]
  def emitError(json: Json): F[Unit]
  def terminate(): F[Unit]
}

object ApolloClient {
  type ConnectionDef[F[_], CP] = TryableDeferred[F, PersistentConnection[F, CP]]
  // Connection =
  //   - None: Not yet connected or disconnected (either by client or by server and client gave up retrying).
  //   - Some(incomplete Deferred): Connection requested but not established.
  //   - Some(complete Deferred): Connection established.
  type Connection[F[_], CP]    = Option[ConnectionDef[F, CP]]
}

abstract class ApolloClient[F[_]: ConcurrentEffect: Timer: Logger, S, CP, CE](
  uri:  Uri,
  name: String
) extends GraphQLPersistentClient[F, S, CP, CE] {
  private val LogPrefix = s"[clue.ApolloClient][${if (name.isEmpty) uri else name}]"

  private val F = implicitly[ConcurrentEffect[F]]

  val connectionStatus: SignallingRef[F, StreamingClientStatus]
  protected val subscriptions: Ref[F, Map[String, Emitter[F]]]
  protected val firstInitInvoked: Deferred[F, Unit]
  protected val connectionRef: Ref[F, ApolloClient.Connection[F, CP]]
  protected val connectionAttempt: Ref[F, Int]

  private val getConnectionDef: F[ApolloClient.ConnectionDef[F, CP]] =
    firstInitInvoked.get >>
      connectionRef.get.flatMap(_ match {
        case None                => F.raiseError(new Exception("Client is disconnected"))
        case Some(connectionDef) => F.pure(connectionDef)
      })

  override def status: F[StreamingClientStatus] =
    connectionStatus.get

  override def statusStream: fs2.Stream[F, StreamingClientStatus] =
    connectionStatus.discrete

  protected def terminateInternal(
    terminateOptions:  TerminateOptions[CP],
    keepSubscriptions: Boolean
  ): F[Unit] =
    getConnectionDef.flatMap(
      _.tryGet.flatMap(
        _.fold(F.unit)(connection =>
          for {
            _ <- connectionStatus.set(StreamingClientStatus.Terminating)
            // Tell server to stop active subscriptions.
            _ <- subscriptions.get.flatMap(
                   _.keySet.toList.traverse(id =>
                     connection.send(StreamingMessage.FromClient.Stop(id))
                   )
                 )
            // Cleanup internal subscriptions and notify clients that subscriptions have ended.
            _ <- terminateAllSubscriptions().whenA(!keepSubscriptions)
            // Follow end protocol with server.
            _ <- connection.send(StreamingMessage.FromClient.ConnectionTerminate)
            _ <- terminateOptions match {
                   case TerminateOptions.KeepConnection              =>
                     connectionStatus.set(StreamingClientStatus.Connected)
                   case TerminateOptions.Disconnect(closeParameters) =>
                     connectionStatus.set(StreamingClientStatus.Disconnecting) >>
                       // Clean up so a new connection can be established if `connect` is called again.
                       connectionRef.set(none) >>
                       // Actually close connection.
                       connection.closeInternal(closeParameters)
                   // Status.Disconnected is set in "processClose"
                 }
          } yield ()
        )
      )
    )

  def createSubscription[D](
    subscriptionStream: Stream[F, D],
    subscriptionId:     String
  ): GraphQLSubscription[F, D] = new GraphQLSubscription[F, D] {
    override val stream: fs2.Stream[F, D] = subscriptionStream

    override def stop(): F[Unit] =
      for {
        connectionDef <- getConnectionDef
        connection    <- connectionDef.get
        _             <- terminateSubscription(subscriptionId)
        _             <- connection.send(StreamingMessage.FromClient.Stop(subscriptionId))
      } yield ()
  }

  type DataQueue[D] = Queue[F, Either[Throwable, Option[D]]]

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

  final protected def terminateSubscription(id: String, lenient: Boolean = false): F[Unit] =
    for {
      _    <- Logger[F].debug(s"$LogPrefix Terminating subscription [$id]")
      subs <- subscriptions.get
      _    <- Logger[F].debug(s"$LogPrefix Current subscriptions: [${subs.keySet}]")
      _    <- subs
                .get(id)
                .fold[F[Unit]](
                  if (lenient) F.unit
                  else F.raiseError(new InvalidSubscriptionIdException(id))
                )(_.terminate())
    } yield ()

  final protected def terminateAllSubscriptions(): F[Unit] =
    for {
      subs <- subscriptions.get
      _    <- subs.toList.traverse(_._2.terminate())
      _    <- subscriptions.set(Map.empty)
    } yield ()

  private def processMessage(str: String): F[Unit] =
    decode[StreamingMessage.FromServer](str) match {
      case Left(e)                                                       =>
        Logger[F].error(e)(s"Exception decoding WebSocket message for [$uri]")
      case Right(StreamingMessage.FromServer.ConnectionError(json))      =>
        Logger[F].error(s"Connection error on WebSocket for [$uri]: $json")
      case Right(StreamingMessage.FromServer.DataJson(id, data, errors)) =>
        subscriptions.get
          .map(_.get(id))
          .flatMap(
            _.fold(
              Logger[F].error(
                s"Received data for non existant subscription id [$id] on WebSocket for [$uri]: $data"
              )
            )(emitter => errors.fold(emitter.emitData(data))(emitter.emitError))
          )
      case Right(StreamingMessage.FromServer.Error(id, json))            =>
        Logger[F].error(
          s"Error message received on WebSocket for [$uri] and subscription id [$id]:\n$json"
        )
      case Right(StreamingMessage.FromServer.Complete(id))               =>
        terminateSubscription(id, lenient = true)
      case _                                                             =>
        F.unit
    }

  private def restartSubscriptions(sender: PersistentConnection[F, CP]): F[Unit] =
    for {
      subs <- subscriptions.get
      _    <- subs.toList.traverse { case (id, emitter) =>
                sender.send(StreamingMessage.FromClient.Start(id, emitter.request))
              }
    } yield ()

  def init(payload: F[Map[String, Json]]): F[Unit] = {

    def reconnect(closeReason: CloseReason[CE]): F[Unit] =
      for {
        attempt   <- connectionAttempt.updateAndGet(_ + 1)
        backoffOpt = reconnectionStrategy(attempt, closeReason)
        _         <- backoffOpt.fold(connectionRef.set(none) >> terminateAllSubscriptions())(backoff =>
                       Timer[F].sleep(backoff).whenA(backoff > Duration.Zero) >> init(payload)
                     )
      } yield ()

    def processError(t: Throwable): F[Unit] =
      connectionRef
        .getAndSet(none)
        .flatMap(_ match {
          case Some(connectionDef) =>
            connectionDef.tryGet.flatMap(_ match {
              // Connection was already established. We must cancel all subscriptions. (or not?)
              case Some(connection) =>
                for {
                  _ <- terminateAllSubscriptions()
                  _ <- connectionStatus.set(StreamingClientStatus.Disconnecting)
                  _ <- connection.close()
                  _ <- connectionStatus.set(StreamingClientStatus.Disconnected)
                } yield ()
              // Connection wasn't established yet.
              case _                => F.unit
            })
          case _                   => F.unit
        })
        .handleErrorWith(t => Logger[F].error(t)(s"Error processing error on WebSocket for [$uri]"))
        .flatMap(_ => reconnect(t.asLeft))

    def processDisconnect(closeEvent: CE): F[Unit] =
      (for {
        _ <- connectionStatus.set(StreamingClientStatus.Disconnected)
        _ <- reconnect(closeEvent.asRight)
      } yield ()).handleErrorWith(t =>
        Logger[F].error(t)(s"Error processing close on WebSocket for [$uri]")
      )

    def createConnection(): F[ApolloClient.ConnectionDef[F, CP]] =
      (for {
        d          <- Deferred.tryable[F, PersistentConnection[F, CP]]
        _          <- connectionRef.set(d.some)
        _          <- firstInitInvoked.complete(()).attempt.void
        _          <- connectionStatus.set(StreamingClientStatus.Connecting)
        connection <- backend.connect(uri, processMessage _, processError _, processDisconnect)
        _          <- d.complete(connection)
        _          <- connectionStatus.set(StreamingClientStatus.Connected)
      } yield d).onError(_ => connectionRef.set(none))

    def initializeConnection(connection: PersistentConnection[F, CP]): F[Unit] =
      (for {
        p <- payload
        _ <- connectionStatus.set(StreamingClientStatus.Initializing)
        _ <- connection.send(StreamingMessage.FromClient.ConnectionInit(p))
        _ <- restartSubscriptions(connection)
        _ <- connectionStatus.set(StreamingClientStatus.Initialized)
        _ <- connectionAttempt.set(0)
      } yield ()).handleErrorWith(t => reconnect(t.asLeft))

    connectionRef.get
      .flatMap(_ match {
        case None =>
          for {
            connectionDef <- createConnection()
            connection    <- connectionDef.get
            _             <- initializeConnection(connection)
          } yield ()
        case _    => F.unit
      })
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

  private def startSubscription[D: Decoder](
    subscription:  String,
    operationName: Option[String],
    variables:     Option[Json]
  ): F[SubscriptionInfo[D]] = {
    val request = GraphQLRequest(subscription, operationName, variables)

    for {
      connectionDef <- getConnectionDef
      connection    <- connectionDef.get
      idEmitter     <- buildQueue[D](request)
      (id, emitter)  = idEmitter
    } yield {
      val bracket =
        Stream.bracket(
          Logger[F].debug(s"$LogPrefix Acquiring queue for subscription [$id]") >>
            subscriptions.update(_ + (id -> emitter))
        )(_ =>
          Logger[F].debug(s"$LogPrefix Releasing queue for subscription[$id]") >>
            subscriptions.update(_ - id)
        )

      val stream = bracket.flatMap(_ =>
        (
          Stream.eval(connection.send(StreamingMessage.FromClient.Start(id, request))) >>
            emitter.queue.dequeue
              .evalTap(v => Logger[F].debug(s"$LogPrefix Dequeuing for subscription [$id]: [$v]"))
        ).rethrow.unNoneTerminate
          .onError { case t: Throwable =>
            Stream.eval(Logger[F].error(t)(s"$LogPrefix Error in subscription [$id]: "))
          }
      )

      SubscriptionInfo(createSubscription(stream, id), terminateSubscription(id))
    }
  }

  override protected def subscribeInternal[D: Decoder](
    subscription:  String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[GraphQLSubscription[F, D]] =
    startSubscription(subscription, operationName, variables)(implicitly[Decoder[D]])
      .map(_.subscription)

  protected def requestInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[D] =
    F.asyncF[D] { cb =>
      startSubscription[D](document, operationName, variables).flatMap { subscriptionInfo =>
        subscriptionInfo.subscription.stream.attempt
          .evalMap(result => F.delay(cb(result)) >> subscriptionInfo.onComplete)
          .compile
          .drain
      }
    }
}
