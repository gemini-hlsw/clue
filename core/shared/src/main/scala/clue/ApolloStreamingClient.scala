package clue

import fs2.Stream
import cats._
import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import io.circe._
import io.circe.parser._
import fs2.concurrent.Queue
import java.util.UUID
import cats.effect.concurrent.{ MVar, Ref }
import cats.data.EitherT
import scala.concurrent.duration._
import scala.language.postfixOps
import fs2.concurrent.SignallingRef
import io.chrisdavenport.log4cats.Logger
import sttp.model.Uri

trait BackendConnection[F[_]] {
  def send(msg: StreamingMessage): F[Unit]
  def close(): F[Unit]
}

trait StreamingBackend[F[_]] {
  def connect(
    uri:       Uri,
    onMessage: String => F[Unit],
    onError:   Throwable => F[Unit],
    onClose:   Boolean => F[Unit] // Boolean = wasClean
  ): F[BackendConnection[F]]
}

object StreamingBackend {
  def apply[F[_]: StreamingBackend]: StreamingBackend[F] = implicitly
}

protected[clue] trait Emitter[F[_]] {
  val request: GraphQLRequest

  def emitData(json:  Json): F[Unit]
  def emitError(json: Json): F[Unit]
  def terminate(): F[Unit]
}

class ApolloStreamingClient[F[_]: ConcurrentEffect: Timer: Logger: StreamingBackend](
  uri:                        Uri
)(
  val connectionStatus:       SignallingRef[F, StreamingClientStatus],
  private val subscriptions:  Ref[F, Map[String, Emitter[F]]],
  private val connectionMVar: MVar[F, Either[Throwable, BackendConnection[F]]]
) extends GraphQLStreamingClient[F] {
  private val LogPrefix = "[clue.ApolloStreamingClient]"

  def status: F[StreamingClientStatus] =
    connectionStatus.get

  def statusStream: fs2.Stream[F, StreamingClientStatus] =
    connectionStatus.discrete

  def close(): F[Unit] =
    connectionMVar.read.rethrow.flatMap(s =>
      connectionStatus.set(StreamingClientStatus.Closing) *> s.close()
    )

  type Subscription[D] = ApolloSubscription[D]

  case class ApolloSubscription[D](stream: Stream[F, D], private val id: String)
      extends StoppableSubscription[D] {

    def stop(): F[Unit] =
      (for {
        sender <- EitherT(connectionMVar.read)
        _      <- EitherT(terminateSubscription(id).attempt)
        _      <- EitherT.right[Throwable](sender.send(StreamingMessage.Stop(id)))
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
      val error = new GraphQLException(List(json))
      queue.enqueue1(Left(error))
    }

    def terminate(): F[Unit] =
      queue.enqueue1(Right(None))
  }

  final protected def terminateSubscription(id: String): F[Unit] =
    for {
      _       <- Logger[F].debug(s"$LogPrefix Terminating subscription [$id]")
      subs    <- subscriptions.get
      _       <- Logger[F].trace(s"$LogPrefix Current subscriptions: [${subs.keySet}]")
      emitter <- Sync[F].fromOption(subs.get(id), new InvalidSubscriptionIdException(id))
      _       <- emitter.terminate()
    } yield ()

  final protected def terminateAllSubscriptions(): F[Unit] =
    for {
      subs <- subscriptions.get
      _    <- subs.toList.traverse(_._2.terminate())
      // _ <- subs.toList.parUnorderedTraverse(_._2.terminate()) // In 2.13 we can just subs.parUnorderedTraverse(_.terminate())
      _    <- subscriptions.set(Map.empty)
    } yield ()

  private val connect: F[Unit] = {

    def processMessage(str: String): F[Unit] =
      decode[StreamingMessage](str) match {
        case Left(e)                                       =>
          Logger[F].error(e)(s"Exception decoding WebSocket message for [$uri]")
        case Right(StreamingMessage.ConnectionError(json)) =>
          Logger[F].error(s"Connection error on WebSocket for [$uri]: $json")
        case Right(StreamingMessage.DataJson(id, json))    =>
          subscriptions.get
            .map(_.get(id))
            .flatMap(
              _.fold(
                Logger[F].error(
                  s"Received data for non existant subscription id [$id] on WebSocket for [$uri]: $json"
                )
              )(_.emitData(json))
            )
        case Right(StreamingMessage.Error(id, json))       =>
          Logger[F].error(
            s"Error message received on WebSocket for [$uri] and subscription id [$id]:\n$json"
          )
        case Right(StreamingMessage.Complete(id))          =>
          terminateSubscription(id)
        case _                                             => ().pure[F]
      }

    def processError(t: Throwable): F[Unit] =
      connectionMVar
        .tryPut(Left(t))
        .flatMap {
          // Connection was established. We must cancel all subscriptions. (or not?)
          case false => terminateAllSubscriptions()
          case true  => connectionStatus.set(StreamingClientStatus.Closed) // Retry?
        }

    val processClose: F[Unit] =
      for {
        _ <- connectionMVar.take
        _ <- connectionStatus.set(StreamingClientStatus.Closed)
        _ <- Timer[F].sleep(10 seconds) // TODO: Backoff.
        // math.min(60000, math.max(200, value.nextAttempt * 2)))
        _ <- connect
      } yield ()

    def restartSubscriptions(sender: BackendConnection[F]): F[Unit] =
      for {
        subs <- subscriptions.get
        _    <- subs.toList.traverse {
               case (id, emitter) => // _ <- subs.toList.parUnorderedTraverse{ case(id, emitter) =>
                 sender.send(StreamingMessage.Start(id, emitter.request))
             }
      } yield ()

    val initializedSender =
      for {
        _      <- connectionStatus.set(StreamingClientStatus.Connecting)
        sender <-
          StreamingBackend[F].connect(uri, processMessage _, processError _, _ => processClose)
        _      <- connectionStatus.set(StreamingClientStatus.Open)
        _      <- sender.send(StreamingMessage.ConnectionInit())
        _      <- restartSubscriptions(sender)
      } yield sender

    initializedSender.attempt.flatMap(connectionMVar.put)
  }

  private def buildQueue[D: Decoder](
    request: GraphQLRequest
  ): F[(String, QueueEmitter[D])] =
    for {
      queue  <- Queue.unbounded[F, Either[Throwable, Option[D]]]
      id      = UUID.randomUUID().toString
      emitter = QueueEmitter(queue, request)
    } yield (id, emitter)

  protected def subscribeInternal[D: Decoder](
    subscription:  String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[Subscription[D]] = {
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
      ApolloSubscription(
        bracket.flatMap(_ =>
          (
            Stream.eval(sender.send(StreamingMessage.Start(id, request))) >>
              emitter.queue.dequeue
                .evalTap(v => Logger[F].trace(s"$LogPrefix Dequeuing for subscription [$id]: [$v]"))
          ).rethrow.unNoneTerminate
            .onError {
              case t: Throwable =>
                Stream.eval(Logger[F].error(t)(s"$LogPrefix Error in subscription [$id]: "))
            }
        ),
        id
      )
    }).value.rethrow
  }

  protected def queryInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[D] =
    // Cleanup happens automatically (as long as the server sends the "Complete" message).
    Async[F].asyncF[D] { cb =>
      subscribeInternal[D](document, operationName, variables).flatMap { subscription =>
        subscription.stream.attempt
          .evalMap(result => Sync[F].delay(cb(result)))
          .compile
          .drain
      }
    }
}

object ApolloStreamingClient {
  def of[F[_]: ConcurrentEffect: Timer: Logger: StreamingBackend](
    uri: Uri
  ): F[ApolloStreamingClient[F]] =
    for {
      connectionStatus <- SignallingRef[F, StreamingClientStatus](StreamingClientStatus.Closed)
      subscriptions    <- Ref.of[F, Map[String, Emitter[F]]](Map.empty)
      connectionMVar   <- MVar.empty[F, Either[Throwable, BackendConnection[F]]]
      client            = new ApolloStreamingClient[F](uri)(connectionStatus, subscriptions, connectionMVar)
      _                <- client.connect
    } yield client
}
