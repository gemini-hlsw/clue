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
import cats.effect.concurrent.{MVar, Ref}
import cats.data.EitherT
import scala.concurrent.duration._
import scala.language.postfixOps
import fs2.concurrent.SignallingRef
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.log4s.Log4sLogger

trait ApolloStreamingClient[F[_]] extends GraphQLStreamingClient[F] {
  protected implicit val ceF: ConcurrentEffect[F]
  protected implicit val tF: Timer[F]  

  protected implicit val logger = Log4sLogger.createLocal[F]

  private val connectionStatus: SignallingRef[F, StreamingClientStatus] =
    SignallingRef
      .in[SyncIO, F, StreamingClientStatus](StreamingClientStatus.Closed)
      .unsafeRunSync()

  def status: F[StreamingClientStatus] =
    connectionStatus.get

  def statusStream: fs2.Stream[F, StreamingClientStatus] =
    connectionStatus.discrete


  def close(): F[Unit] =
    client.read.rethrow.flatMap( s =>
      connectionStatus.set(StreamingClientStatus.Closing) *> s.close()
    )

  type Subscription[D] = ApolloSubscription[D]

  case class ApolloSubscription[D](stream: Stream[F, D], private val id: String)
      extends StoppableSubscription[D] {

    def stop(): F[Unit] =
        (for {
          sender <- EitherT(client.read)
          _ <- EitherT(terminateSubscription(id).attempt)
          _ <- EitherT.right[Throwable](sender.send(StreamingMessage.Stop(id)))
        } yield ()).value.rethrow
  }

  private trait Emitter {
    val request: GraphQLRequest

    def emitData(json:  Json): F[Unit]
    def emitError(json: Json): F[Unit]
    def terminate(): F[Unit]
  }

  type DataQueue[D] = Queue[F, Either[Throwable, Option[D]]]

  private case class QueueEmitter[D: Decoder](
    val queue:   DataQueue[D],
    val request: GraphQLRequest
  ) extends Emitter {

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

  private val subscriptions: Ref[F, Map[String, Emitter]] = 
    Ref.in[SyncIO, F, Map[String, Emitter]](Map.empty).unsafeRunSync()

  protected type WebSocketClient

  protected trait Sender {
    def send(msg: StreamingMessage): F[Unit]
    protected[clue] def close(): F[Unit]
  }

  final protected def processMessage(str: String): F[Unit] = 
    decode[StreamingMessage](str) match {
      case Left(e) =>
        Logger[F].error(e)(s"Exception decoding WebSocket message for [$url]")
      case Right(StreamingMessage.ConnectionError(json)) =>
        Logger[F].error(s"Connection error on WebSocket for [$url]: $json")
      case Right(StreamingMessage.DataJson(id, json)) =>
        subscriptions.get.map(_.get(id)).flatMap(
          _.fold(
            Logger[F].error(s"Received data for non existant subscription id [$id] on WebSocket for [$url]: $json")
          )(_.emitData(json))
        )
      case Right(StreamingMessage.Error(id, json)) =>
        Logger[F].error(s"Error message received on WebSocket for [$url] and subscription id [$id]:\n$json")
      case Right(StreamingMessage.Complete(id)) =>
        terminateSubscription(id)
      case _ => Applicative[F].pure(())
    }

  final protected def terminateSubscription(id: String): F[Unit] =
    (for {
      emitter <- EitherT(subscriptions.get.map(_.get(id).toRight[Throwable](new InvalidSubscriptionIdException(id))))
      _ <- EitherT.right[Throwable](emitter.terminate())
    } yield ()).value.rethrow

  final protected def terminateAllSubscriptions(): F[Unit] = 
    for {
      subs <- subscriptions.get
      _ <- subs.toList.traverse(_._2.terminate())
      // _ <- subs.toList.parUnorderedTraverse(_._2.terminate()) // In 2.13 we can just subs.parUnorderedTraverse(_.terminate())
      _ <- subscriptions.set(Map.empty)
    } yield ()

  protected def createClientInternal(
    onOpen:    Sender => F[Unit],
    onMessage: String => F[Unit],
    onError:   Throwable => F[Unit],
    onClose:   Boolean => F[Unit] // Boolean = wasClean
  ): F[Unit]

  private def createClient(
    mvar: MVar[F, Either[Throwable, Sender]],
    onOpen: Sender => F[Unit] = _ => Applicative[F].pure(())): F[Unit] =
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
                _ <- Timer[F].sleep(10 seconds) // TODO: Backoff.
                // math.min(60000, math.max(200, value.nextAttempt * 2)))
                _      <- createClient(mvar, {sender =>
                  // Restart subscriptions on new client.
                  // TODO Filter out queries/mutations?
                    for {
                      subs <- subscriptions.get
                      _ <- subs.toList.traverse{ case(id, emitter) => 
                      // _ <- subs.toList.parUnorderedTraverse{ case(id, emitter) => 
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

  lazy private val client: MVar[F, Either[Throwable, Sender]] = {
    val mvar = MVar.emptyIn[SyncIO, F, Either[Throwable, Sender]].unsafeRunSync()

    Effect[F].toIO(createClient(mvar)).unsafeRunAsyncAndForget()

    mvar
  }

  private def buildQueue[D: Decoder](
    request: GraphQLRequest
  ): F[(String, QueueEmitter[D])] =
    for {
      queue <- Queue.unbounded[F, Either[Throwable, Option[D]]]
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
      sender    <- EitherT(client.read)
      idEmitter <- EitherT.right[Throwable](buildQueue[D](request))
      (id, emitter) = idEmitter
    } yield {
      val bracket = Stream.bracket(subscriptions.update(_ + (id -> emitter)))(
          _ => subscriptions.update(_ - id))
      ApolloSubscription(
        bracket.flatMap(_ => 
          Stream.eval(sender.send(StreamingMessage.Start(id, request))) >>
            emitter.queue.dequeue.rethrow.unNoneTerminate
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
