// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.data.Ior
import cats.data.NonEmptyList
import cats.syntax.all.*
import io.circe.*
import io.circe.syntax.given

import StreamingMessage.FromClient
import StreamingMessage.FromServer

/**
 * JSON codecs for `clue.model`.
 */
package object json {

  given [V: Encoder]: Encoder[GraphQLRequest[V]] =
    Encoder.instance: a =>
      Json
        .obj(
          "query"         -> Json.fromString(a.query.value),
          "operationName" -> a.operationName.asJson,
          "variables"     -> a.variables.asJson,
          "extensions"    -> a.extensions.asJson
        )
        .dropNullValues

  given [V: Decoder]: Decoder[GraphQLRequest[V]] =
    Decoder.instance: c =>
      for
        query         <- c.get[String]("query")
        operationName <- c.get[Option[String]]("operationName")
        variables     <- c.get[Option[V]]("variables")
        extensions    <- c.get[Option[GraphQLExtensions]]("extensions")
      yield GraphQLRequest(GraphQLQuery(query), operationName, variables, extensions)

  // ---- FromClient

  given Encoder[FromClient.ConnectionInit] =
    Encoder.instance: a =>
      Json
        .obj(
          "type"    -> Json.fromString("connection_init"),
          "payload" -> a.payload.asJson
        )
        .dropNullValues

  given Decoder[FromClient.ConnectionInit] =
    Decoder.instance: c =>
      for
        _ <- checkType(c, "connection_init")
        p <- c.get[Option[JsonObject]]("payload")
      yield FromClient.ConnectionInit(p)

  given Encoder[FromClient.Pong] =
    Encoder.instance: a =>
      Json
        .obj(
          "type"    -> Json.fromString("pong"),
          "payload" -> a.payload.asJson
        )
        .dropNullValues

  given Decoder[FromClient.Pong] =
    Decoder.instance: c =>
      for
        _ <- checkType(c, "pong")
        p <- c.get[Option[JsonObject]]("payload")
      yield FromClient.Pong(p)

  given Encoder[FromClient.Subscribe] =
    Encoder.instance: a =>
      Json.obj(
        "type"    -> Json.fromString("subscribe"),
        "id"      -> Json.fromString(a.id),
        "payload" -> a.payload.asJson
      )

  given Decoder[FromClient.Subscribe] =
    Decoder.instance: c =>
      for
        _ <- checkType(c, "subscribe")
        i <- c.get[String]("id")
        p <- c.get[GraphQLRequest[JsonObject]]("payload")
      yield FromClient.Subscribe(i, p)

  given FromClientCompleteEncoder: Encoder[FromClient.Complete] =
    Encoder.instance: a =>
      Json.obj(
        "type" -> Json.fromString("complete"),
        "id"   -> Json.fromString(a.id)
      )

  given FromClientCompleteDecoder: Decoder[FromClient.Complete] =
    Decoder.instance: c =>
      for
        _ <- checkType(c, "complete")
        i <- c.get[String]("id")
      yield FromClient.Complete(i)

  given Encoder[StreamingMessage.FromClient] =
    Encoder.instance:
      case m @ FromClient.ConnectionInit(_) => m.asJson
      case m @ FromClient.Pong(_)           => m.asJson
      case m @ FromClient.Subscribe(_, _)   => m.asJson
      case m @ FromClient.Complete(_)       => m.asJson

  given Decoder[StreamingMessage.FromClient] =
    Decoder.instance: c =>
      c.get[String]("type")
        .flatMap:
          case "connection_init" => Decoder[FromClient.ConnectionInit].widen(c)
          case "pong"            => Decoder[FromClient.Pong].widen(c)
          case "subscribe"       => Decoder[FromClient.Subscribe].widen(c)
          case "complete"        => Decoder[FromClient.Complete].widen(c)
          case other             =>
            DecodingFailure(
              s"Unexpected StreamingMessage.FromClient with type [$other]",
              c.history
            ).asLeft

  // ---- FromServer

  given Encoder[FromServer.ConnectionAck] =
    Encoder.instance: a =>
      Json
        .obj(
          "type"    -> Json.fromString("connection_ack"),
          "payload" -> a.payload.asJson
        )
        .dropNullValues

  given Decoder[FromServer.ConnectionAck] =
    Decoder.instance: c =>
      for
        _ <- checkType(c, "connection_ack")
        p <- c.get[Option[JsonObject]]("payload")
      yield FromServer.ConnectionAck(p)

  given Encoder[FromServer.Ping] =
    Encoder.instance: a =>
      Json
        .obj(
          "type"    -> Json.fromString("ping"),
          "payload" -> a.payload.asJson
        )
        .dropNullValues

  given Decoder[FromServer.Ping] =
    Decoder.instance: c =>
      for
        _ <- checkType(c, "ping")
        p <- c.get[Option[JsonObject]]("payload")
      yield FromServer.Ping(p)

  given Encoder[GraphQLError.PathElement] =
    (a: GraphQLError.PathElement) => a.fold(_.asJson, _.asJson)

  given Decoder[GraphQLError.PathElement] =
    Decoder.instance: c =>
      if (c.value.isNumber)
        c.as[Int].map(GraphQLError.PathElement.int)
      else
        c.as[String].map(GraphQLError.PathElement.string)

  given Encoder[GraphQLError.Location] =
    Encoder.instance: a =>
      Json.obj(
        "line"   -> a.line.asJson,
        "column" -> a.column.asJson
      )

  given Decoder[GraphQLError.Location] =
    Decoder.instance: c =>
      for
        line   <- c.get[Int]("line")
        column <- c.get[Int]("column")
      yield GraphQLError.Location(line, column)

  given Encoder.AsObject[GraphQLError] =
    Encoder.AsObject.instance: a =>
      JsonObject(
        "message"    -> a.message.asJson,
        "path"       -> a.path.asJson,
        "locations"  -> a.locations.asJson,
        "extensions" -> a.extensions.asJson
      ).filter { case (_, v) => !v.isNull }

  given Decoder[GraphQLError] =
    Decoder.instance: c =>
      for
        message    <- c.get[String]("message")
        path       <- c.get[Option[NonEmptyList[GraphQLError.PathElement]]]("path")
        locations  <- c.get[Option[NonEmptyList[GraphQLError.Location]]]("locations")
        extensions <- c.get[Option[GraphQLExtensions]]("extensions")
      yield GraphQLError(message, path, locations, extensions)

  given [D: Encoder]: Encoder[GraphQLDataResponse[D]] =
    Encoder.instance: a =>
      Json
        .obj("data" -> a.data.asJson)
        .deepMerge(
          Json
            .obj(
              "errors"     -> a.errors.asJson,
              "extensions" -> a.extensions.asJson
            )
            .dropNullValues
        )

  given [D: Decoder]: Decoder[GraphQLDataResponse[D]] =
    Decoder.instance: c =>
      for
        data       <- c.get[D]("data")
        errors     <- c.get[Option[GraphQLErrors]]("errors")
        extensions <- c.get[Option[GraphQLExtensions]]("extensions")
      yield GraphQLDataResponse(data, errors, extensions)

  given [D: Encoder]: Encoder[GraphQLResponse[D]] =
    Encoder.instance: a =>
      Json
        .obj(
          "data"       -> a.data.asJson,
          "errors"     -> a.errors.asJson,
          "extensions" -> a.extensions.asJson
        )
        .dropNullValues

  given [D: Decoder]: Decoder[GraphQLResponse[D]] =
    Decoder.instance: c =>
      for
        data       <- c.get[Option[D]]("data")
        errors     <- c.get[Option[GraphQLErrors]]("errors")
        extensions <- c.get[Option[GraphQLExtensions]]("extensions")
        result     <-
          Ior
            .fromOptions(errors, data)
            .fold[Decoder.Result[Ior[GraphQLErrors, D]]](
              DecodingFailure("Response doesn't contain 'data' or 'errors' block", c.history).asLeft
            )(_.asRight)
      yield GraphQLResponse(result, extensions)

  given Encoder[FromServer.Next] =
    Encoder.instance: a =>
      Json.obj(
        "type"    -> Json.fromString("data"),
        "id"      -> Json.fromString(a.id),
        "payload" -> a.payload.asJson
      )

  given Decoder[FromServer.Next] =
    Decoder.instance: c =>
      for
        _ <- checkType(c, "data")
        i <- c.get[String]("id")
        p <- c.get[GraphQLResponse[Json]]("payload")
      yield FromServer.Next(i, p)

  implicit val EncoderError: Encoder[FromServer.Error] =
    Encoder.instance: a =>
      Json.obj(
        "type"    -> Json.fromString("error"),
        "id"      -> Json.fromString(a.id),
        "payload" -> a.payload.asJson
      )

  given Decoder[FromServer.Error] =
    Decoder.instance: c =>
      for
        _ <- checkType(c, "error")
        i <- c.get[String]("id")
        p <- c.get[GraphQLErrors]("payload")
      yield FromServer.Error(i, p)

  given FromServerCompleteEncoder: Encoder[FromServer.Complete] =
    Encoder.instance: a =>
      Json.obj(
        "type" -> Json.fromString("complete"),
        "id"   -> Json.fromString(a.id)
      )

  given FromServerCompleteDecoder: Decoder[FromServer.Complete] =
    Decoder.instance: c =>
      for {
        _ <- checkType(c, "complete")
        i <- c.get[String]("id")
      } yield FromServer.Complete(i)

  given Encoder[StreamingMessage.FromServer] =
    Encoder.instance:
      case m @ FromServer.ConnectionAck(_) => m.asJson
      case m @ FromServer.Ping(_)          => m.asJson
      case m @ FromServer.Next(_, _)       => m.asJson
      case m @ FromServer.Error(_, _)      => m.asJson
      case m @ FromServer.Complete(_)      => m.asJson

  given Decoder[StreamingMessage.FromServer] =
    Decoder.instance: c =>
      c.get[String]("type")
        .flatMap:
          case "connection_ack" => c.as[FromServer.ConnectionAck]
          case "ping"           => c.as[FromServer.Ping]
          case "data"           => c.as[FromServer.Next]
          case "error"          => c.as[FromServer.Error]
          case "complete"       => c.as[FromServer.Complete]
          case other            =>
            DecodingFailure(
              s"Unexpected StreamingMessage.FromServer with type [$other]",
              c.history
            ).asLeft

  private def checkType(c: HCursor, expected: String): Decoder.Result[Unit] =
    c.get[String]("type")
      .filterOrElse(_ === expected, DecodingFailure(s"expected '$expected''", c.history))
      .void

  private def encodeCaseObject(tpe: String): Json =
    Json.obj("type" -> Json.fromString(tpe))

  private def decodeCaseObject[A, W >: A](tpe: String, instance: A): Decoder[W] =
    Decoder.instance[A](c => checkType(c, tpe).as(instance)).widen[W]

}
