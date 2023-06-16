// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.effect._
import cats.syntax.all._
import clue._
import clue.model.GraphQLRequest
import clue.model.json._
import io.circe.Encoder
import io.circe.syntax._
import org.scalajs.dom.Fetch
import org.scalajs.dom.Headers
import org.scalajs.dom.HttpMethod
import org.scalajs.dom.RequestInit
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.scalajs.js.URIUtils
import scala.util.Failure
import scala.util.Success
import org.scalajs.dom.AbortController
import cats.Applicative

sealed trait FetchMethod extends Product with Serializable
object FetchMethod {
  case object GET  extends FetchMethod
  case object POST extends FetchMethod
}

final class FetchJSBackend[F[_]: Async](fetchMethod: FetchMethod)
    extends FetchBackend[F, FetchJSRequest] {
  override def request[V: Encoder](
    request:     GraphQLRequest[V],
    baseRequest: FetchJSRequest
  ): F[String] =
    Async[F].async { cb =>
      val controller = new AbortController()
      val _signal    = controller.signal
      val _headers   = new Headers(baseRequest.headers)
      val fetch      = fetchMethod match {
        case FetchMethod.POST =>
          _headers.set("Content-Type", "application/json")
          Fetch
            .fetch(
              baseRequest.uri.toString,
              new RequestInit {
                method = HttpMethod.POST
                body = request.asJson.toString
                headers = _headers
                signal = _signal
              }
            )
        case FetchMethod.GET  =>
          val variables = request.variables.foldMap(v => s"&variables=${v.asJson.noSpaces}")
          val op        = request.operationName.foldMap(o => s"&operationName=$o")
          Fetch
            .fetch(
              URIUtils.encodeURI(
                s"${baseRequest.uri}?query=${request.query.trim.replaceAll(" +", " ")}$variables$op"
              ),
              new RequestInit {
                method = HttpMethod.GET
                headers = _headers
                signal = _signal
              }
            )
      }

      Applicative[F]
        .pure(
          fetch.toFuture
            .flatMap(_.text().toFuture)
            .onComplete {
              case Success(r) => cb(Right(r))
              case Failure(t) => cb(Left(t))
            }
        )
        .as(Sync[F].delay(controller.abort()).some)
    }
}

object FetchJSBackend {
  def apply[F[_]: Async](method: FetchMethod = FetchMethod.POST): FetchJSBackend[F] =
    new FetchJSBackend[F](method)
}
