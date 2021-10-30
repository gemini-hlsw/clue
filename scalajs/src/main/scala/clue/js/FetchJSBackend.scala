// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.effect._
import cats.syntax.all._
import clue._
import clue.model.GraphQLRequest
import clue.model.json._
import io.circe.syntax._
import org.http4s.Headers
import org.http4s.Uri
import org.scalajs.dom.Fetch
import org.scalajs.dom.HttpMethod
import org.scalajs.dom.RequestInit
import org.scalajs.dom.{ Headers => FetchHeaders }

import scala.concurrent.ExecutionContext.Implicits._
import scala.scalajs.js.URIUtils
import scala.util.Failure
import scala.util.Success

sealed trait FetchMethod extends Product with Serializable
object FetchMethod {
  case object GET  extends FetchMethod
  case object POST extends FetchMethod
}

final class FetchJSBackend[F[_]: Async](method: FetchMethod) extends TransactionalBackend[F] {
  def request(
    uri:     Uri,
    request: GraphQLRequest,
    headers: Headers
  ): F[String] =
    Async[F].async_ { cb =>
      val headersʹ = new FetchHeaders()
      headers.headers.foreach(h => headersʹ.append(h.name.toString, h.value))
      val fetch    = method match {
        case FetchMethod.POST =>
          headersʹ.set("Content-Type", "application/json")
          Fetch
            .fetch(uri.toString,
                   new RequestInit {
                     method = HttpMethod.POST
                     body = request.asJson.toString
                     headers = headersʹ
                   }
            )
        case FetchMethod.GET  =>
          val variables = request.variables.foldMap(v => s"&variables=${v.noSpaces}")
          val op        = request.operationName.foldMap(o => s"&operationName=$o")
          Fetch
            .fetch(
              URIUtils.encodeURI(
                s"$uri?query=${request.query.trim.replaceAll(" +", " ")}$variables$op"
              ),
              new RequestInit {
                method = HttpMethod.GET
                headers = headersʹ
              }
            )
      }

      fetch.toFuture
        .flatMap(_.text().toFuture)
        .onComplete {
          case Success(r) => cb(Right(r))
          case Failure(t) => cb(Left(t))
        }
    }
}

object FetchJSBackend {
  def apply[F[_]: Async](method: FetchMethod = FetchMethod.POST): FetchJSBackend[F] =
    new FetchJSBackend[F](method)
}
