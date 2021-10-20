// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.syntax.all._
import cats.effect._
import clue._
import clue.model.GraphQLRequest
import clue.model.json._
import io.circe.syntax._
import org.http4s.Uri
import org.scalajs.dom.ext.Ajax
import scala.scalajs.js.URIUtils

import scala.concurrent.ExecutionContext.Implicits._
import scala.util.Failure
import scala.util.Success

sealed trait AjaxMethod extends Product with Serializable

object AjaxMethod {
  case object GET  extends AjaxMethod
  case object POST extends AjaxMethod
}

final class AjaxJSBackend[F[_]: Async](method: AjaxMethod) extends TransactionalBackend[F] {
  def request(
    uri:     Uri,
    request: GraphQLRequest
  ): F[String] =
    Async[F].async_ { cb =>
      method match {
        case AjaxMethod.POST =>
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

        case AjaxMethod.GET =>
          val variables = request.variables.foldMap(v => s"&variables=${v.noSpaces}")
          val op        = request.operationName.foldMap(o => s"&operationName=$o")
          Ajax
            .get(
              url = URIUtils.encodeURI(s"$uri?query=${request.query.trim.replaceAll(" +", " ")}$variables$op")
            )
            .onComplete {
              case Success(r) => cb(Right(r.responseText))
              case Failure(t) => cb(Left(t))
            }
      }
    }
}

object AjaxJSBackend {
  def apply[F[_]: Async](method: AjaxMethod = AjaxMethod.POST): AjaxJSBackend[F] =
    new AjaxJSBackend[F](method)
}
