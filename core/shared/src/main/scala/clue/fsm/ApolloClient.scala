package clue.fsm

import clue._

import clue.GraphQLStreamingClient
import cats.effect.concurrent.Ref
import cats.effect.implicits._
import sttp.model.Uri
import clue.GraphQLSubscription
import io.circe.Json
import io.circe.Decoder

import cats.syntax.all._
import cats.effect.concurrent.Deferred
import java.util.UUID
import cats.effect.ConcurrentEffect
import io.chrisdavenport.log4cats.Logger
import scala.annotation.unused
import cats.effect.ExitCase

trait ApolloClient[F[_], S] extends GraphQLStreamingClient[F, S] {
  def connect(): F[Unit]
  def initialize(): F[Unit]

  def terminate(): F[Unit]
  def disconnect(): F[Unit]
}

trait WebSocketHandler[F[_], CE] {
  // protected def onOpen(): F[Unit]
  protected def onMessage(msg: String): F[Unit]
  protected def onError(t:     Throwable): F[Unit]
  protected def onClose(event: CE): F[Unit]
}

trait StreaminClientInternal[F[_]] {
  protected def stopSubscription(id: ApolloClient.SubscriptionId): F[Unit]
}

protected sealed trait State[+F[_]]
protected object State {
  final case object Disconnected  extends State[Nothing]
  final case class Connecting[F[_]](latch: Deferred[F, Either[Throwable, Unit]], attempt: Int = 0)
      extends State[F]
  final case class Connected[F[_], CP](connection: PersistentConnection[F, CP]) extends State[F]
  final case object Initializing  extends State[Nothing]
  final case object Initialized   extends State[Nothing]
  final case object Terminating   extends State[Nothing]
  final case object Disconnecting extends State[Nothing]
  final case object RetryWait     extends State[Nothing]
}

class ApolloClientImpl[F[_], S, CP, CE](uri: Uri, name: String, state: Ref[F, State[F]])(implicit
  F:                                         ConcurrentEffect[F],
  backend:                                   PersistentBackend[F, CP, CE],
  logger:                                    Logger[F]
) extends ApolloClient[F, S]
    with WebSocketHandler[F, CE]
    with StreaminClientInternal[F] {
  import State._

  @unused private implicit val logPrefix: LogPrefix = new LogPrefix(
    s"clue.ApolloClient[${if (name.isEmpty) uri else name}]"
  )

  override def connect(): F[Unit] = {
    val warn = logger.warn("connect() called while already connected or attempting to connect.")

    Deferred[F, Either[Throwable, Unit]].flatMap { newLatch =>
      state.modify {
        case Disconnected             => Connecting(newLatch) -> openConnection(newLatch).rethrow
        case s @ Connecting(latch, _) => s                    -> (warn >> latch.get.rethrow)
        case state                    => state                -> warn
      }.flatten
    }
  }

  private def openConnection(
    latch: Deferred[F, Either[Throwable, Unit]]
  ): F[Either[Throwable, Unit]] = {
    for {
      connection <- backend.connect(uri, onMessage, onError, onClose).attempt
      _          <- state.set { // We could use update/modify here for extra safety, but it seems overkill.
                      connection match {
                        case Left(_)  => Disconnected // TODO Retry
                        case Right(c) => Connected(c)
                      }
                    }
      _          <- latch.complete(connection.void) // TODO Don't release latch when retrying.
    } yield connection.void
  }.guaranteeCase {
    case ExitCase.Completed | ExitCase.Error(_) => F.unit
    case ExitCase.Canceled                      =>
      state.modify {
        case s @ Connected(_) => s -> latch.complete(Either.unit).attempt.void
        case Connecting(_, _) =>
          Disconnected -> latch
            .complete("connect() was interrupted.".error[Unit])
            .attempt
            .void
        case s                =>
          s -> F.pure(s"Unexpected state [$s] in canceled connect(). Cannot recover.").error[Unit]
      }.flatten
  }

  // override protected def onOpen(): F[Unit] = ???

  override def initialize(): F[Unit] = ???

  override def terminate(): F[Unit] = ???

  override def disconnect(): F[Unit] = ???

  override protected def requestInternal[D: Decoder](
    document:      String,
    operationName: Option[String],
    variables:     Option[Json]
  ): F[D] = ???

  override protected def subscribeInternal[D: Decoder](
    document:      String,
    operationName: Option[String],
    variables:     Option[Json]
  ): F[GraphQLSubscription[F, D]] = ???

  override protected def stopSubscription(id: ApolloClient.SubscriptionId): F[Unit] = ???

  override protected def onMessage(msg: String): F[Unit] = ???

  override protected def onError(t: Throwable): F[Unit] = ???

  override protected def onClose(event: CE): F[Unit] = ???
}

object ApolloClient {
  type SubscriptionId = UUID

  def apply[F[_]: ConcurrentEffect: Logger, S, CP, CE](
    uri:              Uri,
    name:             String
  )(implicit backend: PersistentBackend[F, CP, CE]): F[ApolloClient[F, S]] =
    for {
      state <- Ref[F].of[State[F]](State.Disconnected)
    } yield new ApolloClientImpl(uri, name, state)
}
