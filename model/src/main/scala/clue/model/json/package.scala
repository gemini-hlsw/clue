// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.data.Ior
import cats.syntax.all._
import io.circe._
import io.circe.syntax._

/**
 * JSON codecs for `clue.model`.
 */
package object json {

  implicit def encoderGraphQLRequest[V: Encoder]: Encoder[GraphQLRequest[V]] =
    Encoder.instance(a =>
      Json
        .obj(
          "query"         -> Json.fromString(a.query),
          "operationName" -> a.operationName.asJson,
          "variables"     -> a.variables.asJson
        )
        .dropNullValues
    )

  implicit def decoderGraphQLRequest[V: Decoder]: Decoder[GraphQLRequest[V]] =
    Decoder.instance(c =>
      for {
        query         <- c.downField("query").as[String]
        operationName <- c.downField("operationName").as[Option[String]]
        variables     <- c.downField("variables").as[Option[V]]
      } yield GraphQLRequest(query, operationName, variables)
    )

  // ---- FromClient

  import StreamingMessage.FromClient._

  implicit val EncoderConnectionInit: Encoder[ConnectionInit] =
    Encoder.instance(a =>
      Json.obj(
        "type"    -> Json.fromString("connection_init"),
        "payload" -> a.payload.asJson
      )
    )

  implicit val DecoderConnectionInit: Decoder[ConnectionInit] =
    Decoder.instance(c =>
      for {
        _ <- checkType(c, "connection_init")
        p <- c.downField("payload").as[Map[String, Json]]
      } yield ConnectionInit(p)
    )

  implicit val EncoderStart: Encoder[Start] =
    Encoder.instance(a =>
      Json.obj(
        "type"    -> Json.fromString("start"),
        "id"      -> Json.fromString(a.id),
        "payload" -> a.payload.asJson
      )
    )

  implicit val DecoderStart: Decoder[Start] =
    Decoder.instance(c =>
      for {
        _ <- checkType(c, "start")
        i <- c.downField("id").as[String]
        p <- c.downField("payload").as[GraphQLRequest[Json]]
      } yield Start(i, p)
    )

  implicit val EncoderStop: Encoder[Stop] =
    Encoder.instance(a =>
      Json.obj(
        "type" -> Json.fromString("stop"),
        "id"   -> Json.fromString(a.id)
      )
    )

  implicit val DecoderStop: Decoder[Stop] =
    Decoder.instance(c =>
      for {
        _ <- checkType(c, "stop")
        i <- c.downField("id").as[String]
      } yield Stop(i)
    )

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
    Decoder.instance(c =>
      c.downField("type")
        .as[String]
        .flatMap {
          case "connection_init"      => Decoder[ConnectionInit].widen(c)
          case "start"                => Decoder[Start].widen(c)
          case "stop"                 => Decoder[Stop].widen(c)
          case "connection_terminate" => Decoder[ConnectionTerminate.type].widen(c)
          case other                  =>
            DecodingFailure(
              s"Unexpected StreamingMessage.FromClient with type [$other]",
              c.history
            ).asLeft
        }
    )

  // ---- FromServer

  import StreamingMessage.FromServer._

  implicit val DecoderConnectionAck: Decoder[ConnectionAck.type] =
    decodeCaseObject("connection_ack", ConnectionAck)

  implicit val EncoderConnectionError: Encoder[ConnectionError] =
    Encoder.instance(a =>
      Json.obj(
        "type"    -> Json.fromString("connection_error"),
        "payload" -> a.payload.asJson
      )
    )

  implicit val DecoderConnectionError: Decoder[ConnectionError] =
    Decoder.instance(c =>
      for {
        _ <- checkType(c, "connection_error")
        p <- c.downField("payload").as[Json]
      } yield ConnectionError(p)
    )

  implicit val DecoderConnectionKA: Decoder[ConnectionKeepAlive.type] =
    decodeCaseObject("ka", ConnectionKeepAlive)

  implicit def encoderGraphQLDataResponse[D: Encoder]: Encoder[GraphQLDataResponse[D]] =
    Encoder.instance(a =>
      Json
        .obj(
          "data"   -> a.data.asJson,
          "errors" -> a.errors.map(_.asJson).getOrElse(Json.Null)
        )
        .dropNullValues
    )

  implicit def decoderGraphQLDataResponse[D: Decoder]: Decoder[GraphQLDataResponse[D]] =
    Decoder.instance(c =>
      for {
        data   <- c.downField("data").as[D]
        errors <- c.downField("errors").as[Option[GraphQLErrors]]
      } yield GraphQLDataResponse(data, errors)
    )

  implicit val EncoderData: Encoder[Data] =
    Encoder.instance(a =>
      Json.obj(
        "type"    -> Json.fromString("data"),
        "id"      -> Json.fromString(a.id),
        "payload" -> a.payload.asJson
      )
    )

  implicit val DecoderData: Decoder[Data] =
    Decoder.instance(c =>
      for {
        _ <- checkType(c, "data")
        i <- c.downField("id").as[String]
        p <- c.downField("payload").as[GraphQLDataResponse[Json]]
      } yield Data(i, p)
    )

  implicit val EncoderError: Encoder[Error] =
    Encoder.instance(a =>
      Json.obj(
        "type"    -> Json.fromString("error"),
        "id"      -> Json.fromString(a.id),
        "payload" -> a.payload.asJson
      )
    )

  implicit val DecoderError: Decoder[Error] =
    Decoder.instance(c =>
      for {
        _ <- checkType(c, "error")
        i <- c.downField("id").as[String]
        p <- c.downField("payload").as[GraphQLErrors]
      } yield Error(i, p)
    )

  implicit val EncoderComplete: Encoder[Complete] =
    Encoder.instance(a =>
      Json.obj(
        "type" -> Json.fromString("complete"),
        "id"   -> Json.fromString(a.id)
      )
    )

  implicit val DecoderComplete: Decoder[Complete] =
    Decoder.instance(c =>
      for {
        _ <- checkType(c, "complete")
        i <- c.downField("id").as[String]
      } yield Complete(i)
    )

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
    Decoder.instance(c =>
      c.downField("type")
        .as[String]
        .flatMap {
          case "connection_ack"   => c.as[ConnectionAck.type]
          case "connection_error" => c.as[ConnectionError]
          case "ka"               => c.as[ConnectionKeepAlive.type]
          case "data"             => c.as[Data]
          case "error"            => c.as[Error]
          case "complete"         => c.as[Complete]
          case other              =>
            DecodingFailure(
              s"Unexpected StreamingMessage.FromServer with type [$other]",
              c.history
            ).asLeft
        }
    )

  implicit val EncoderGraphQLErrorPathElement: Encoder[GraphQLError.PathElement] =
    (a: GraphQLError.PathElement) => a.fold(_.asJson, _.asJson)

  implicit val DecoderGraphQLErrorPathElement: Decoder[GraphQLError.PathElement] =
    Decoder.instance(c =>
      c.as[Int]
        .map(GraphQLError.PathElement.int)
        .orElse(c.as[String].map(GraphQLError.PathElement.string))
        .orElse(DecodingFailure(s"Unexpected PathElement", c.history).asLeft)
    )

  implicit val EncoderGraphQLErrorLocation: Encoder[GraphQLError.Location] =
    Encoder.instance(a =>
      Json.obj(
        "line"   -> a.line.asJson,
        "column" -> a.column.asJson
      )
    )

  implicit val DecoderGraphQLErrorLocation: Decoder[GraphQLError.Location] =
    Decoder.instance(c =>
      for {
        line   <- c.get[Int]("line")
        column <- c.get[Int]("column")
      } yield GraphQLError.Location(line, column)
    )

  private def optionalField[A: Encoder](name: String, a: A)(
    predicate:                                A => Boolean
  ): Option[(String, Json)] =
    if (predicate(a)) (name, a.asJson).some else none

  implicit val EncoderGraphQLError: Encoder[GraphQLError] =
    Encoder.instance(a =>
      Json.fromFields(
        List(
          ("message" -> a.message.asJson).some,
          optionalField[List[GraphQLError.PathElement]]("path", a.path)(_.nonEmpty),
          optionalField[List[GraphQLError.Location]]("locations", a.locations)(_.nonEmpty),
          optionalField[Map[String, String]]("extensions", a.extensions)(_.nonEmpty)
        ).flattenOption
      )
    )

  implicit val DecoderGraphQLError: Decoder[GraphQLError] =
    Decoder.instance(c =>
      for {
        message    <- c.get[String]("message")
        path       <- c.getOrElse[List[GraphQLError.PathElement]]("path")(List.empty)
        locations  <- c.getOrElse[List[GraphQLError.Location]]("locations")(List.empty)
        extensions <- c.getOrElse[Map[String, String]]("extensions")(Map.empty)
      } yield GraphQLError(message, path, locations, extensions)
    )

  implicit def DecoderGraphQLCombinedResponse[D: Decoder]: Decoder[GraphQLCombinedResponse[D]] =
    Decoder.instance(c =>
      for {
        data   <- c.get[Option[D]]("data")
        errors <- c.get[Option[GraphQLErrors]]("errors")
        result <-
          Ior
            .fromOptions(errors, data)
            .fold[Decoder.Result[GraphQLCombinedResponse[D]]](
              DecodingFailure("Response doesn't contain 'data' or 'errors' block", c.history).asLeft
            )(_.asRight)
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
