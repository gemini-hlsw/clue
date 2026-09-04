// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.effect.*
import cats.effect.std.SecureRandom
import cats.syntax.all.*
import clue.PersistentClientStatus
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.testing.TestingLogger

import scala.concurrent.duration.*

/** Common fixtures for the tests of the WebSocket client. */
trait ApolloClientSuite extends CatsEffectSuite:

  protected given Logger[IO] = TestingLogger.impl[IO]()

  protected val Timeout: FiniteDuration = 5.seconds

  /** The wait before a test asserts that the client sent no further message. */
  protected val Settle: FiniteDuration = 100.millis

  override def munitIOTimeout: Duration = 10.seconds

  protected def clientOn(
    backend:  TestWebSocketBackend[IO],
    strategy: ReconnectionStrategy = ReconnectionStrategy.never,
    connect:  Boolean = true
  )(using Logger[IO]): IO[ApolloClient[IO, String, Unit]] =
    for
      given SecureRandom[IO]            <- SecureRandom.javaSecuritySecureRandom[IO]
      given WebSocketBackend[IO, String] = backend
      client                            <- ApolloClient.of[IO, String, Unit]("ws://test", "test", strategy)
      _                                 <- client.connect().whenA(connect)
    yield client

  /** Builds a client with a logger that the test can inspect. */
  protected def clientWithLogger(
    backend:  TestWebSocketBackend[IO],
    strategy: ReconnectionStrategy = ReconnectionStrategy.never,
    connect:  Boolean = true
  ): IO[(ApolloClient[IO, String, Unit], TestingLogger[IO])] =
    val logger = TestingLogger.impl[IO]()
    clientOn(backend, strategy, connect)(using logger).tupleRight(logger)

  /** Reconnects on a close with the reason "retry", and gives up on any other. */
  protected val retryOnce: ReconnectionStrategy = (_, reason) =>
    reason match
      case Right(Right(CloseParams(_, Some("retry")))) => Duration.Zero.some
      case _                                           => none

  protected def awaitStatus(
    client: ApolloClient[IO, String, Unit],
    status: PersistentClientStatus
  ): IO[Unit] =
    client.statusStream.find(_ == status).compile.drain

  /** The messages that the logger recorded at the error level. */
  protected def errors(logger: TestingLogger[IO]): IO[Vector[String]] =
    logger.logged.map(_.collect { case TestingLogger.ERROR(m, _) => m })
