// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import java.util.UUID

import cats.data.EitherT
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

protected[clue] trait Emitter[F[_]] {
  val request: GraphQLRequest

  def emitData(json:  Json): F[Unit]
  def emitError(json: Json): F[Unit]
  def terminate(): F[Unit]
}

abstract class ApolloStreamingClient[F[_]: ConcurrentEffect: Timer: Logger, S](uri: Uri)
    extends GraphQLPersistentStreamingClient[F, S] {
  private val LogPrefix = "[clue.ApolloStreamingClient]"

  private val F = implicitly[ConcurrentEffect[F]]

  override protected implicit val backend: PersistentBackend[F]

  val connectionStatus: SignallingRef[F, StreamingClientStatus]
  protected val subscriptions: Ref[F, Map[String, Emitter[F]]]
  protected val connectionMVar: MVar2[F, Either[Throwable, backend.Connection]]
  protected val connectionAttempt: Ref[F, Int]

  override def status: F[StreamingClientStatus] =
    connectionStatus.get

  override def statusStream: fs2.Stream[F, StreamingClientStatus] =
    connectionStatus.discrete

  override def disconnectInternal(closeParameters: Option[backend.CP]): F[Unit] =
    connectionMVar.read.rethrow.flatMap(connection =>
      connectionStatus.set(StreamingClientStatus.Disconnecting) >> connection.closeInternal(
        closeParameters
      )
    )

  def createSubscription[D](
    subscriptionStream: Stream[F, D],
    subscriptionId:     String
  ): GraphQLSubscription[F, D] = new GraphQLSubscription[F, D] {
    override val stream: fs2.Stream[F, D] = subscriptionStream

    override def stop(): F[Unit] =
      (for {
        sender <- EitherT(connectionMVar.read)
        _      <- EitherT(terminateSubscription(subscriptionId).attempt)
        _      <- EitherT.right[Throwable](sender.send(StreamingMessage.FromClient.Stop(subscriptionId)))
      } yield ()).value.rethrow
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

  private def processError(t: Throwable): F[Unit] =
    connectionMVar
      .tryPut(Left(t))
      .flatMap {
        // Connection was already established. We must cancel all subscriptions. (or not?)
        case false => terminateAllSubscriptions()
        case true  => connectionStatus.set(StreamingClientStatus.Disconnected) // Retry?
      }
      .handleErrorWith(t => Logger[F].error(t)(s"Error processing error on WebSocket for [$uri]"))

  private def restartSubscriptions(sender: backend.Connection): F[Unit] =
    for {
      subs <- subscriptions.get
      _    <- subs.toList.traverse { case (id, emitter) =>
                sender.send(StreamingMessage.FromClient.Start(id, emitter.request))
              }
    } yield ()

  def connect(
    payload:              F[JsonObject],
    reconnectionStrategy: Option[ReconnectionStrategy[F, backend.CE]] // If None, no reconnect
  ): F[Unit] = {

    def processClose(closeEvent: backend.CE): F[Unit] =
      (for {
        _         <- connectionMVar.take
        _         <- connectionStatus.set(StreamingClientStatus.Disconnected)
        attempt   <- connectionAttempt.updateAndGet(_ + 1)
        backoffOpt = reconnectionStrategy.flatMap(_.backoffFn(attempt, closeEvent))
        _         <- backoffOpt.fold(F.unit)(backoff =>
                       Timer[F].sleep(backoff) >> connect(payload, reconnectionStrategy)
                     )
      } yield ()).handleErrorWith(t =>
        Logger[F].error(t)(s"Error processing close on WebSocket for [$uri]")
      )

    val initializedSender =
      for {
        _      <- connectionStatus.set(StreamingClientStatus.Connecting)
        sender <- backend.connect(uri, processMessage _, processError _, processClose)
        _      <- connectionAttempt.set(0)
        _      <- connectionStatus.set(StreamingClientStatus.Connected)
        p      <- payload
        _      <- sender.send(StreamingMessage.FromClient.ConnectionInit(p))
        _      <- restartSubscriptions(sender)
      } yield sender

    initializedSender.attempt.flatMap(connectionMVar.put)
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

    (for {
      sender       <- EitherT(connectionMVar.read)
      idEmitter    <- EitherT.right[Throwable](buildQueue[D](request))
      (id, emitter) = idEmitter
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
          Stream.eval(sender.send(StreamingMessage.FromClient.Start(id, request))) >>
            emitter.queue.dequeue
              .evalTap(v => Logger[F].debug(s"$LogPrefix Dequeuing for subscription [$id]: [$v]"))
        ).rethrow.unNoneTerminate
          .onError { case t: Throwable =>
            Stream.eval(Logger[F].error(t)(s"$LogPrefix Error in subscription [$id]: "))
          }
      )

      SubscriptionInfo(createSubscription(stream, id), terminateSubscription(id))
    }).value.rethrow
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

object ApolloStreamingClient {
  def of[F[_]: ConcurrentEffect: Timer: Logger, S](
    uri:               Uri
  )(implicit _backend: PersistentBackend[F]): F[ApolloStreamingClient[F, S]] =
    for {
      _connectionStatus  <-
        SignallingRef[F, StreamingClientStatus](StreamingClientStatus.Disconnected)
      _subscriptions     <- Ref.of[F, Map[String, Emitter[F]]](Map.empty)
      _connectionMVar    <- MVar.empty[F, Either[Throwable, _backend.Connection]]
      _connectionAttempt <- Ref.of[F, Int](0)
      client              =
        new ApolloStreamingClient[F, S](uri) {
          override protected implicit val backend = _backend

          override val connectionStatus            = _connectionStatus
          override protected val subscriptions     = _subscriptions
          override protected val connectionMVar    =
            _connectionMVar.asInstanceOf[MVar2[F, Either[Throwable, backend.Connection]]]
          override protected val connectionAttempt = _connectionAttempt
        }
    } yield client
}
