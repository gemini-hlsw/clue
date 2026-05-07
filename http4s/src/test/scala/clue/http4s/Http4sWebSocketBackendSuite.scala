// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4s

import cats.Foldable
import cats.effect.*
import cats.syntax.all.*
import clue.ConnectionId
import clue.websocket.*
import munit.CatsEffectSuite
import org.http4s.Uri
import org.http4s.client.websocket.*

import scala.concurrent.duration.*

class Http4sWebSocketBackendSuite extends CatsEffectSuite:

  test("close releases high-level connection and only notifies onClose once") {
    for {
      releases   <- Ref.of[IO, Int](0)
      closes     <- Ref.of[IO, Int](0)
      deferred   <- Deferred[IO, WSFrame.Close]
      wsConn      = new WSConnectionHighLevel[IO]:
                      override def send(wsf: WSDataFrame): IO[Unit]                                 = IO.unit
                      override def sendMany[G[_]: Foldable, A <: WSDataFrame](wsfs: G[A]): IO[Unit] =
                        IO.unit
                      override def receive: IO[Option[WSDataFrame]]                                 = IO.never
                      override def subprotocol: Option[String]                                      = none
                      override def closeFrame: Deferred[IO, WSFrame.Close]                          = deferred
      wsClient    = new WSClient[IO]:
                      override def connect(request: WSRequest): Resource[IO, WSConnection[IO]] =
                        Resource.eval(IO.raiseError(new RuntimeException("unused")))
                      override def connectHighLevel(
                        request: WSRequest
                      ): Resource[IO, WSConnectionHighLevel[IO]] =
                        Resource.make(IO.pure(wsConn))(_ => releases.update(_ + 1))
      handler     = new WebSocketHandler[IO]:
                      override def onMessage(connectionId: ConnectionId, msg: String): IO[Unit]     =
                        IO.unit
                      override def onClose(connectionId: ConnectionId, event: CloseEvent): IO[Unit] =
                        closes.update(_ + 1)
      connection <- Http4sWebSocketBackend[IO](wsClient).connect(
                      Uri.unsafeFromString("ws://example.test/ws"),
                      handler,
                      ConnectionId.Zero
                    )
      _          <- connection.close()
      _          <- connection.close()
      _          <- IO.sleep(100.millis)
      releaseN   <- releases.get
      closeN     <- closes.get
    } yield {
      assertEquals(releaseN, 1)
      assertEquals(closeN, 1)
    }
  }
