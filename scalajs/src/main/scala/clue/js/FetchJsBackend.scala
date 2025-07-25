// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.Applicative
import cats.effect.*
import cats.syntax.all.*
import clue.*
import clue.model.GraphQLRequest
import clue.model.json.given
import io.circe.Encoder
import io.circe.syntax.*
import org.scalajs.dom.AbortController
import org.scalajs.dom.Fetch
import org.scalajs.dom.Headers
import org.scalajs.dom.HttpMethod
import org.scalajs.dom.RequestInit
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js.URIUtils
import scala.util.Failure
import scala.util.Success

sealed trait FetchMethod extends Product with Serializable
object FetchMethod {
  case object GET  extends FetchMethod
  case object POST extends FetchMethod
}

final class FetchJsBackend[F[_]: Async](fetchMethod: FetchMethod)
    extends FetchBackend[F, FetchJsRequest] {
  override def request[V: Encoder](
    request:     GraphQLRequest[V],
    baseRequest: FetchJsRequest
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
                s"${baseRequest.uri}?query=${request.query.value.trim.replaceAll(" +", " ")}$variables$op"
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

object FetchJsBackend {
  def apply[F[_]: Async](method: FetchMethod = FetchMethod.POST): FetchJsBackend[F] =
    new FetchJsBackend[F](method)
}
