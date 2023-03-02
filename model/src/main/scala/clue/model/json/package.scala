// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.syntax.all._
import io.circe._
import io.circe.syntax._
import cats.data.NonEmptyList
import cats.data.Ior

/**
 * JSON codecs for `clue.model`.
 */
package object json {

  implicit val EncoderGraphQLRequest: Encoder[GraphQLRequest] =
    (a: GraphQLRequest) =>
      Json
        .obj(
          "query"         -> Json.fromString(a.query),
          "operationName" -> a.operationName.asJson,
          "variables"     -> a.variables.asJson
        )
        .dropNullValues

  implicit val DecoderGraphQLRequest: Decoder[GraphQLRequest] =
    (c: HCursor) =>
      for {
        query         <- c.downField("query").as[String]
        operationName <- c.downField("operationName").as[Option[String]]
        variables     <- c.downField("variables").as[Option[Json]]
      } yield GraphQLRequest(query, operationName, variables)

  // ---- FromClient

  import StreamingMessage.FromClient._

  implicit val EncoderConnectionInit: Encoder[ConnectionInit] =
    (a: ConnectionInit) =>
      Json.obj(
        "type"    -> Json.fromString("connection_init"),
        "payload" -> a.payload.asJson
      )

  implicit val DecoderConnectionInit: Decoder[ConnectionInit] =
    (c: HCursor) =>
      for {
        _ <- checkType(c, "connection_init")
        p <- c.downField("payload").as[Map[String, Json]]
      } yield ConnectionInit(p)

  implicit val EncoderStart: Encoder[Start] =
    (a: Start) =>
      Json.obj(
        "type"    -> Json.fromString("start"),
        "id"      -> Json.fromString(a.id),
        "payload" -> a.payload.asJson
      )

  implicit val DecoderStart: Decoder[Start] =
    (c: HCursor) =>
      for {
        _ <- checkType(c, "start")
        i <- c.downField("id").as[String]
        p <- c.downField("payload").as[GraphQLRequest]
      } yield Start(i, p)

  implicit val EncoderStop: Encoder[Stop] =
    (a: Stop) =>
      Json.obj(
        "type" -> Json.fromString("stop"),
        "id"   -> Json.fromString(a.id)
      )

  implicit val DecoderStop: Decoder[Stop] =
    (c: HCursor) =>
      for {
        _ <- checkType(c, "stop")
        i <- c.downField("id").as[String]
      } yield Stop(i)

  implicit val DecoderConnectionTerminate: Decoder[ConnectionTerminate.type] =
    decodeCaseObject("connection_terminate", ConnectionTerminate)

  implicit val EncoderFromClient: Encoder[StreamingMessage.FromClient] =
    Encoder.instance {
      case m @ ConnectionInit(_)  => m.asJson
      case m @ Start(_, _)        => m.asJson
      case m @ Stop(_)            => m.asJson
      case _ @ConnectionTerminate => encodeCaseObject("connection_terminate")
    }

  implicit val DecoderFromClient: Decoder[StreamingMessage.FromClient] =
    (c: HCursor) =>
      c.downField("type")
        .as[String]
        .flatMap {
          case "connection_init"      => Decoder[ConnectionInit].widen(c)
          case "start"                => Decoder[Start].widen(c)
          case "stop"                 => Decoder[Stop].widen(c)
          case "connection_terminate" => Decoder[ConnectionTerminate.type].widen(c)
          case other                  =>
            Left(
              DecodingFailure(s"Unexpected StreamingMessage.FromClient with type [$other]",
                              c.history
              )
            )
        }

  // ---- FromServer

  import StreamingMessage.FromServer._

  implicit val DecoderConnectionAck: Decoder[ConnectionAck.type] =
    decodeCaseObject("connection_ack", ConnectionAck)

  implicit val EncoderConnectionError: Encoder[ConnectionError] =
    (a: ConnectionError) =>
      Json.obj(
        "type"    -> Json.fromString("connection_error"),
        "payload" -> a.payload.asJson
      )

  implicit val DecoderConnectionError: Decoder[ConnectionError] =
    (c: HCursor) =>
      for {
        _ <- checkType(c, "connection_error")
        p <- c.downField("payload").as[Map[String, Json]]
      } yield ConnectionError(p)

  implicit val DecoderConnectionKA: Decoder[ConnectionKeepAlive.type] =
    decodeCaseObject("ka", ConnectionKeepAlive)

  implicit val EncoderDataWrapper: Encoder[DataWrapper] =
    (a: DataWrapper) =>
      Json
        .obj(
          "data"   -> a.data,
          "errors" -> a.errors.getOrElse(Json.Null)
        )
        .dropNullValues

  implicit val DecoderDataWrapper: Decoder[DataWrapper] =
    (c: HCursor) =>
      for {
        data   <- c.downField("data").as[Json]
        errors <- c.downField("errors").as[Option[Json]]
      } yield DataWrapper(data, errors)

  implicit val EncoderData: Encoder[Data] =
    (a: Data) =>
      Json.obj(
        "type"    -> Json.fromString("data"),
        "id"      -> Json.fromString(a.id),
        "payload" -> a.payload.asJson
      )

  implicit val DecoderData: Decoder[Data] =
    (c: HCursor) =>
      for {
        _ <- checkType(c, "data")
        i <- c.downField("id").as[String]
        p <- c.downField("payload").as[DataWrapper]
      } yield Data(i, p)

  implicit val EncoderError: Encoder[Error] =
    (a: Error) =>
      Json.obj(
        "type"    -> Json.fromString("error"),
        "id"      -> Json.fromString(a.id),
        "payload" -> a.payload
      )

  implicit val DecoderError: Decoder[Error] =
    (c: HCursor) =>
      for {
        _ <- checkType(c, "error")
        i <- c.downField("id").as[String]
        p <- c.downField("payload").as[Json]
      } yield Error(i, p)

  implicit val EncoderComplete: Encoder[Complete] =
    (a: Complete) =>
      Json.obj(
        "type" -> Json.fromString("complete"),
        "id"   -> Json.fromString(a.id)
      )

  implicit val DecoderComplete: Decoder[Complete] =
    (c: HCursor) =>
      for {
        _ <- checkType(c, "complete")
        i <- c.downField("id").as[String]
      } yield Complete(i)

  implicit val EncoderFromServer: Encoder[StreamingMessage.FromServer] =
    Encoder.instance {
      case _ @ConnectionAck       => encodeCaseObject("connection_ack")
      case m @ ConnectionError(_) => m.asJson
      case _ @ConnectionKeepAlive => encodeCaseObject("ka")
      case m @ Data(_, _)         => m.asJson
      case m @ Error(_, _)        => m.asJson
      case m @ Complete(_)        => m.asJson
    }

  implicit val DecoderFromServer: Decoder[StreamingMessage.FromServer] =
    (c: HCursor) =>
      c.downField("type")
        .as[String]
        .flatMap {
          case "connection_ack"   => Decoder[ConnectionAck.type].widen(c)
          case "connection_error" => Decoder[ConnectionError].widen(c)
          case "ka"               => Decoder[ConnectionKeepAlive.type].widen(c)
          case "data"             => Decoder[Data].widen(c)
          case "error"            => Decoder[Error].widen(c)
          case "complete"         => Decoder[Complete].widen.apply(c)
          case other              =>
            Left(
              DecodingFailure(s"Unexpected StreamingMessage.FromServer with type [$other]",
                              c.history
              )
            )
        }

  implicit val EncoderGraphQLErrorPathElement: Encoder[GraphQLError.PathElement] =
    (a: GraphQLError.PathElement) => a.fold(_.asJson, _.asJson)

  implicit val DecoderGraphQLErrorPathElement: Decoder[GraphQLError.PathElement] =
    (c: HCursor) =>
      c.as[Int]
        .map(GraphQLError.PathElement.int)
        .orElse(c.as[String].map(GraphQLError.PathElement.string))
        .orElse(DecodingFailure(s"Unexpected PathElement", c.history).asLeft)

  implicit val EncoderGraphQLErrorLocation: Encoder[GraphQLError.Location] =
    (a: GraphQLError.Location) =>
      Json.obj(
        "line"   -> a.line.asJson,
        "column" -> a.column.asJson
      )

  implicit val DecoderGraphQLErrorLocation: Decoder[GraphQLError.Location] =
    (c: HCursor) =>
      for {
        line   <- c.get[Int]("line")
        column <- c.get[Int]("column")
      } yield GraphQLError.Location(line, column)

  private def optionalField[A: Encoder](name: String, a: A)(
    predicate:                                A => Boolean
  ): Option[(String, Json)] =
    if (predicate(a)) (name, a.asJson).some else none

  implicit val EncoderGraphQLError: Encoder[GraphQLError] =
    (a: GraphQLError) =>
      Json.fromFields(
        List(
          ("message" -> a.message.asJson).some,
          optionalField[List[GraphQLError.PathElement]]("path", a.path)(_.nonEmpty),
          optionalField[List[GraphQLError.Location]]("locations", a.locations)(_.nonEmpty),
          optionalField[Map[String, String]]("extensions", a.extensions)(_.nonEmpty)
        ).flattenOption
      )

  implicit val DecoderGraphQLError: Decoder[GraphQLError] =
    (c: HCursor) =>
      for {
        message    <- c.get[String]("message")
        path       <- c.getOrElse[List[GraphQLError.PathElement]]("path")(List.empty)
        locations  <- c.getOrElse[List[GraphQLError.Location]]("locations")(List.empty)
        extensions <- c.getOrElse[Map[String, String]]("extensions")(Map.empty)
      } yield GraphQLError(message, path, locations, extensions)

  implicit def DecoderGraphQLResponse[D: Decoder]: Decoder[GraphQLResponse[D]] =
    Decoder.instance(c =>
      for {
        data   <- c.get[Option[D]]("data")
        errors <- c.get[Option[NonEmptyList[GraphQLError]]]("errors")
        result <-
          Ior
            .fromOptions(errors, data)
            .fold[Decoder.Result[GraphQLResponse[D]]](
              DecodingFailure("Response didn't contain 'data' or 'errors' block", c.history).asLeft
            )(GraphQLResponse(_).asRight)
      } yield result
    )

  private def checkType(c: HCursor, expected: String): Decoder.Result[Unit] =
    c.downField("type")
      .as[String]
      .filterOrElse(_ === expected, DecodingFailure(s"expected '$expected''", c.history))
      .void

  private def encodeCaseObject(tpe: String): Json =
    Json.obj("type" -> Json.fromString(tpe))

  private def decodeCaseObject[A, W >: A](tpe: String, instance: A): Decoder[W] =
    Decoder.instance[A](c => checkType(c, tpe).as(instance)).widen[W]

}
