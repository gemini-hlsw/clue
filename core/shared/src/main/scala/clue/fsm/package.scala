package clue

package object fsm {
  type ApolloWebSocketClient[F[_], S] = ApolloClient[F, S, WebSocketCloseParams]
}
