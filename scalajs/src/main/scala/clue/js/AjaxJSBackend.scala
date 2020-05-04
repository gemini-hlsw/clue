package clue.js

import clue._
import cats.effect._
import org.scalajs.dom.ext.Ajax
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import sttp.model.Uri

import scala.concurrent.ExecutionContext.Implicits._
import scala.util.Success
import scala.util.Failure

final class AjaxJSBackend[F[_]: Async] extends Backend[F] {
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
