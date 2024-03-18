// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4sjdkDemo

import cats.Applicative
import cats.effect.Async
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all.*
import clue.ErrorPolicy
import clue.FetchClient
import clue.GraphQLOperation
import clue.http4s.Http4sWebSocketBackend
import clue.http4s.Http4sWebSocketClient
import clue.websocket.WebSocketClient
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.*
import org.http4s.implicits.*
import org.http4s.jdkhttpclient.JdkWSClient
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.*
import scala.util.Random

object Demo extends IOApp.Simple {
  type DemoDB

  implicit private val DefaultErrorPolicy: ErrorPolicy.ReturnAlways.type = ErrorPolicy.ReturnAlways

  object Query extends GraphQLOperation.Typed.NoInput[DemoDB, Json] {
    override val document: String = """
      |query {
      |  observations(WHERE: {programId: {EQ: "p-2"}}) {
      |    matches {
      |      id
      |      title
      |      status
      |    }
      |  }
      |}""".stripMargin
  }

  object Subscription extends GraphQLOperation.Typed.NoInput[DemoDB, Json] {
    override val document: String = """
      |subscription {
      |  observationEdit(programId:"p-2") {
      |    id
      |  }
      |}""".stripMargin
  }

  object Mutation extends GraphQLOperation[DemoDB] {
    type Data = Json
    case class Variables(observationId: String, status: String)

    override val document: String                        = """
    |mutation ($observationId: ObservationId!, $status: ObsStatus!){
    |  updateObservations(input: {WHERE: {id: {EQ: $observationId}}, SET: {status: $status}}) {
    |    observations {
    |      id
    |    }
    |  }
    |}""".stripMargin
    override val varEncoder: Encoder.AsObject[Variables] = deriveEncoder

    override val dataDecoder: Decoder[Data] = Decoder[Json]
  }

  def withLogger[F[_]: Sync]: Resource[F, Logger[F]] =
    Resource.make(Slf4jLogger.create[F])(_ => Applicative[F].unit)

  def withStreamingClient[F[_]: Async: Logger]: Resource[F, WebSocketClient[F, DemoDB]] =
    for {
      client <- Resource.eval(JdkWSClient.simple)
      backend = Http4sWebSocketBackend(client)
      uri     = uri"wss://lucuma-odb-development.herokuapp.com/ws"
      sc     <- Resource.eval(Http4sWebSocketClient.of[F, DemoDB](uri)(Async[F], Logger[F], backend))
      _      <- Resource.make(sc.connect() >> sc.initialize())(_ => sc.terminate() >> sc.disconnect())
    } yield sc

  val allStatus =
    List("NEW", "INCLUDED", "PROPOSED", "APPROVED", "FOR_REVIEW", "READY", "ONGOING", "OBSERVED")

  def randomMutate(client: FetchClient[IO, DemoDB], ids: List[String]) =
    for {
      id     <- IO(ids(Random.between(0, ids.length)))
      status <- IO(allStatus(Random.between(0, allStatus.length)))
      _      <-
        client.request(Mutation).withInput(Mutation.Variables(id, status))
    } yield ()

  def mutator(client: FetchClient[IO, DemoDB], ids: List[String]) =
    for {
      _ <- IO.sleep(3.seconds)
      _ <- randomMutate(client, ids)
      _ <- IO.sleep(2.seconds)
      _ <- randomMutate(client, ids)
      _ <- IO.sleep(3.seconds)
      _ <- randomMutate(client, ids)
    } yield ()

  def run =
    withLogger[IO].use { implicit logger =>
      withStreamingClient[IO].use { implicit client =>
        for {
          result         <- client.request(Query)
          _              <- IO.println(result)
          subscription   <- client.subscribe(Subscription).allocated
          (stream, close) = subscription
          fiber          <- stream.evalTap(_ => IO.println("UPDATE!")).compile.drain.start
          _              <-
            mutator(client, (result.result.right.get \\ "id").map(_.as[String].toOption.get)).start
          _              <- IO.sleep(10.seconds)
          _              <- close
          _              <- fiber.join
          result         <- client.request(Query)(ErrorPolicy.RaiseAlways)
          _              <- IO.println(result)

        } yield ()
      }
    }
}
