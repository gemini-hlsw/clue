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
import cats.effect.Timer

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
  final case class Connecting[F[_]](latch: Deferred[F, Either[Throwable, Unit]]) extends State[F]
  final case class Connected[F[_], CP](connection: PersistentConnection[F, CP]) extends State[F]
  final case class Initializing[F[_]](latch: Deferred[F, Either[Throwable, Unit]])
      extends State[Nothing]
  final case object Initialized   extends State[Nothing]
  final case object Terminating   extends State[Nothing]
  final case object Disconnecting extends State[Nothing]
  final case object RetryWait     extends State[Nothing]
}

class ApolloClientImpl[F[_], S, CP, CE](
  uri:                  Uri,
  name:                 String,
  reconnectionStrategy: ReconnectionStrategy[CE],
  state:                Ref[F, State[F]]
)(implicit
  F:                    ConcurrentEffect[F],
  backend:              PersistentBackend[F, CP, CE],
  timer:                Timer[F],
  logger:               Logger[F]
) extends ApolloClient[F, S]
    with WebSocketHandler[F, CE]
    with StreaminClientInternal[F] {
  import State._

  @unused private implicit val logPrefix: LogPrefix = new LogPrefix(
    s"clue.ApolloClient[${if (name.isEmpty) uri else name}]"
  )

  override def connect(): F[Unit] = {
    val warn = "connect() called while already connected or attempting to connect.".warnF

    Deferred[F, Either[Throwable, Unit]].flatMap { newLatch =>
      state.modify {
        case Disconnected          => Connecting(newLatch) -> openConnection(newLatch)
        case s @ Connecting(latch) => s                    -> (warn >> latch.get.rethrow)
        case state                 => state                -> warn
      }.flatten
    }
  }

  private def openConnection(
    latch:   Deferred[F, Either[Throwable, Unit]],
    attempt: Int = 1
  ): F[Unit] =
    backend
      .connect(uri, onMessage, onError, onClose)
      .attempt
      .flatMap { connection =>
        state.modify {
          case s @ Connecting(_) =>
            connection match {
              case Left(t)  =>
                reconnectionStrategy(attempt, t.asLeft) match {
                  case None       => Disconnected -> latch.complete(t.asLeft)
                  case Some(wait) =>
                    s -> (t.warnF("Error in connect(). Retrying...") >>
                      timer.sleep(wait) >>
                      openConnection(latch, attempt + 1))
                }
              case Right(c) => Connected(c) -> latch.complete(().asRight)
            }
          case s                 =>
            s -> (latch.complete(connection.void) >>
              s"Unexpected state [$s] in connect(). Unblocking clients, but state may be inconsistent.".raiseError)
        }.flatten
      }
      .guaranteeCase {
        case ExitCase.Completed | ExitCase.Error(_) => F.unit
        case ExitCase.Canceled                      => // Attempt recovery.
          state.modify {
            case s @ Connected(_) => s -> latch.complete(Either.unit).attempt.void
            case Connecting(_)    =>
              Disconnected -> latch
                .complete("connect() was interrupted.".error[Unit])
                .attempt
                .void
            case s                =>
              s -> s"Unexpected state [$s] in canceled connect(). Cannot recover.".raiseError
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

  def apply[F[_]: ConcurrentEffect: Timer: Logger, S, CP, CE](
    uri:                  Uri,
    name:                 String,
    reconnectionStrategy: ReconnectionStrategy[CE]
  )(implicit backend:     PersistentBackend[F, CP, CE]): F[ApolloClient[F, S]] =
    for {
      state <- Ref[F].of[State[F]](State.Disconnected)
    } yield new ApolloClientImpl(uri, name, reconnectionStrategy, state)
}
