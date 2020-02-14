package clue

import fs2.Stream
import cats._
import cats.effect._
import cats.implicits._
import io.circe._
import io.circe.parser._
import fs2.concurrent.Queue
import java.util.UUID
import cats.effect.concurrent.{MVar, Ref}
import cats.data.EitherT
import scala.concurrent.duration._
import scala.language.postfixOps
import fs2.concurrent.SignallingRef
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.log4s.Log4sLogger

trait ApolloStreamingClient extends GraphQLStreamingClient[ConcurrentEffect] {
  protected implicit val timerIO: Timer[IO]
  protected implicit val csIO: ContextShift[IO]

  protected implicit val logger = Log4sLogger.createLocal[IO]

  private val connectionStatus: SignallingRef[IO, StreamingClientStatus] =
    SignallingRef
      .in[SyncIO, IO, StreamingClientStatus](StreamingClientStatus.Closed)
      .unsafeRunSync()

  private def toF[F[_]: LiftIO]: ~>[IO, F] = new ~>[IO, F] {
        def apply[A](fa: IO[A]): F[A] = LiftIO[F].liftIO(fa)
      }

  def status[F[_]: LiftIO]: F[StreamingClientStatus] =
    LiftIO[F].liftIO(connectionStatus.get)

  def statusStream[F[_]: LiftIO]: fs2.Stream[F, StreamingClientStatus] =
    connectionStatus.discrete.translate(toF)

  def close[F[_]: ConcurrentEffect](): F[Unit] =
    LiftIO[F].liftIO(
      for {
        isEmpty <- client.isEmpty
        sender  <- if (!isEmpty) client.read.map(_.toOption) else IO(None)
        _ <- sender.foldMap(s =>
          connectionStatus.set(StreamingClientStatus.Closing) *> 
            s.close()
        )
      } yield ()
    )

  type Subscription[F[_], D] = ApolloSubscription[F, D]

  case class ApolloSubscription[F[_]: LiftIO, D](stream: Stream[F, D], private val id: String)
      extends StoppableSubscription[F, D] {

    def stop(): F[Unit] =
      LiftIO[F].liftIO(
        (for {
          sender <- EitherT(client.read)
          _ <- EitherT(terminateSubscription(id).attempt)
          _ <- EitherT.right[Throwable](sender.send(StreamingMessage.Stop(id)))
        } yield ()).value.rethrow
      )
  }

  private trait Emitter {
    val request: GraphQLRequest

    def emitData(json:  Json): IO[Unit]
    def emitError(json: Json): IO[Unit]
    def terminate(): IO[Unit]
  }

  type DataQueue[D] = Queue[IO, Either[Throwable, Option[D]]]

  private case class QueueEmitter[D: Decoder](
    val queue:   DataQueue[D],
    val request: GraphQLRequest
  ) extends Emitter {

    def emitData(json: Json): IO[Unit] = {
      val data = json.as[D]
      queue.enqueue1(data.map(_.some))
    }

    def emitError(json: Json): IO[Unit] = {
      val error = new GraphQLException(List(json))
       queue.enqueue1(Left(error))
    }

    def terminate(): IO[Unit] =
       queue.enqueue1(Right(None))
  }

  private val subscriptions: Ref[IO, Map[String, Emitter]] = 
    Ref.in[SyncIO, IO, Map[String, Emitter]](Map.empty).unsafeRunSync()

  protected type WebSocketClient

  protected trait Sender {
    def send(msg: StreamingMessage): IO[Unit]
    protected[clue] def close(): IO[Unit]
  }

  final protected def processMessage(str: String): IO[Unit] = 
    decode[StreamingMessage](str) match {
      case Left(e) =>
        e.printStackTrace()
        Logger[IO].error(e)(s"Exception decoding WebSocket message for [$uri]")
      case Right(StreamingMessage.ConnectionError(json)) =>
        Logger[IO].error(s"Connection error on WebSocket for [$uri]: $json")
      case Right(StreamingMessage.DataJson(id, json)) =>
        for {
          emitter <- subscriptions.get.map(_.get(id))
          _ <- emitter.foldMap(_.emitData(json))
        } yield ()
      case Right(StreamingMessage.Error(id, json)) =>
        Logger[IO].error(s"Error message received on WebSocket for [$uri] and subscription id [$id]:\n$json")
      case Right(StreamingMessage.Complete(id)) =>
        terminateSubscription(id)
      case _ => IO.unit
    }

  final protected def terminateSubscription(id: String): IO[Unit] =
    (for {
      emitter <- EitherT(subscriptions.get.map(_.get(id).toRight[Throwable](new InvalidSubscriptionIdException(id))))
      _ <- EitherT.right[Throwable](emitter.terminate())
    } yield ()).value.rethrow

  final protected def terminateAllSubscriptions(): IO[Unit] = 
    for {
      subs <- subscriptions.get
      _ <- subs.toList.parUnorderedTraverse(_._2.terminate()) // In 2.13 we can just subs.parUnorderedTraverse(_.terminate())
      _ <- subscriptions.set(Map.empty)
    } yield ()

  protected def createClientInternal(
    onOpen:    Sender => IO[Unit],
    onMessage: String => IO[Unit],
    onError:   Throwable => IO[Unit],
    onClose:   Boolean => IO[Unit] // Boolean = wasClean
  ): IO[Unit]

  private def createClient(
    mvar: MVar[IO, Either[Throwable, Sender]],
    onOpen: Sender => IO[Unit] = _ => IO.unit): IO[Unit] =
      connectionStatus.set(StreamingClientStatus.Connecting) *> {
        try {
          createClientInternal(
            onOpen = { sender =>
              for {
                _ <- connectionStatus.set(StreamingClientStatus.Open)
                _ <- sender.send(StreamingMessage.ConnectionInit())
                _ <- mvar.put(Right(sender))
                _ <- onOpen(sender)
              } yield ()
            },
            onMessage = processMessage _,
            onError = { t =>
              mvar
                .tryPut(Left(t))
                .flatMap {
                  // Connection was established. We must cancel all subscriptions. (or not?)
                  case false => terminateAllSubscriptions()
                  case true  => connectionStatus.set(StreamingClientStatus.Closed) // Retry?
                }
            },
            onClose = { _ =>
              for {
                _ <- mvar.take
                _ <- connectionStatus.set(StreamingClientStatus.Closed)
                _ <- IO.sleep(10 seconds) // TODO: Backoff.
                // math.min(60000, math.max(200, value.nextAttempt * 2)))
                _      <- createClient(mvar, {sender =>
                  // Restart subscriptions on new client.
                  // TODO Filter out queries/mutations?
                    for {
                      subs <- subscriptions.get
                      _ <- subs.toList.parUnorderedTraverse{ case(id, emitter) => 
                        sender.send(StreamingMessage.Start(id, emitter.request))
                      }
                    } yield()
                })
              } yield ()
            }
          )
        } catch {
          case e: Throwable =>
            mvar.put(Left(e)) // TODO: Use tryPut and handle error
        }
      }

  lazy private val client: MVar[IO, Either[Throwable, Sender]] = {
    val mvar = MVar.emptyIn[SyncIO, IO, Either[Throwable, Sender]].unsafeRunSync()

    createClient(mvar).unsafeRunAsyncAndForget()

    mvar
  }

  private def buildQueue[D: Decoder](
    request: GraphQLRequest
  ): IO[(String, QueueEmitter[D])] =
    for {
      queue <- Queue.unbounded[IO, Either[Throwable, Option[D]]]
      id      = UUID.randomUUID().toString
      emitter = QueueEmitter(queue, request)
    } yield (id, emitter)

  protected def subscribeInternal[F[_]: ConcurrentEffect, D: Decoder](
    subscription:  String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[Subscription[F, D]] = {
    val request = GraphQLRequest(subscription, operationName, variables)

    LiftIO[F].liftIO(
      (for {
        sender    <- EitherT(client.read)
        idEmitter <- EitherT.right[Throwable](buildQueue[D](request))
        (id, emitter) = idEmitter
        _ <- EitherT.right[Throwable](sender.send(StreamingMessage.Start(id, request)))
      } yield {
        val bracket = Stream.bracket(subscriptions.update(_ + (id -> emitter)))(
            _ => subscriptions.update(_ - id))
        ApolloSubscription(
          bracket.flatMap(_ => emitter.queue.dequeue.rethrow.unNoneTerminate).translate(toF), 
          id
        )
      }).value.rethrow
    )
  }

  protected def queryInternal[F[_]: ConcurrentEffect, D: Decoder](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[D] =
    // Cleanup happens automatically (as long as the server sends the "Complete" message).
    LiftIO[F].liftIO {
      IO.asyncF[D] { cb =>
        subscribeInternal[IO, D](document, operationName, variables).flatMap { subscription =>
          subscription.stream.attempt
            .evalMap(result => IO(cb(result)))
            .compile
            .drain
        }
      }
    }
}
