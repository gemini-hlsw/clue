// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import scala.concurrent.ExecutionContext.Implicits._
import scala.util.Failure
import scala.util.Success

import cats.effect._
import clue._
import clue.model.GraphQLRequest
import clue.model.json._
import io.circe.syntax._
import org.scalajs.dom.ext.Ajax
import sttp.model.Uri

final class AjaxJSBackend[F[_]: Async] extends TransactionalBackend[F] {
  def request(
    uri:     Uri,
    request: GraphQLRequest
  ): F[String] =
    Async[F].async { cb =>
      Ajax
        .post(
          url = uri.toString,
          data = request.asJson.toString,
          headers = Map("Content-Type" -> "application/json")
        )
        .onComplete {
          case Success(r) => cb(Right(r.responseText))
          case Failure(t) => cb(Left(t))
        }
    }
}

object AjaxJSBackend {
  def apply[F[_]: Async]: AjaxJSBackend[F] = new AjaxJSBackend[F]
}
