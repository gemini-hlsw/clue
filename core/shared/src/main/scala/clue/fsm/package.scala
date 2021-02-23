package clue

import cats.effect.concurrent.Deferred

package object fsm {
  type ApolloWebSocketClient[F[_], S] = ApolloClient[F, S, WebSocketCloseParams]

  protected[clue] type Latch[F[_]] = Deferred[F, Either[Throwable, Unit]]

}

package fsm {

  import cats.effect.Concurrent
  object Latch {
    def apply[F[_]: Concurrent]: F[Latch[F]] =
      Deferred[F, Either[Throwable, Unit]]
  }
}
