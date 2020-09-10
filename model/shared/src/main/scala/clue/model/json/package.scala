// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.syntax.all._
import io.circe._
import io.circe.syntax._

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
        p <- c.downField("payload").as[Map[String, String]]
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

  implicit val EncoderFromClient: Encoder[StreamingMessage.FromClient] =
    Encoder.instance {
      case m @ ConnectionInit(_)  => m.asJson
      case m @ Start(_, _)        => m.asJson
      case m @ Stop(_)            => m.asJson
      case _ @ConnectionTerminate => encodeCaseObject("connection_terminate")
    }

  implicit val DecoderFromClient: Decoder[StreamingMessage.FromClient] =
    List[Decoder[StreamingMessage.FromClient]](
      Decoder[ConnectionInit].widen,
      Decoder[Start].widen,
      Decoder[Stop].widen,
      decodeCaseObject("connection_terminate", ConnectionTerminate)
    ).reduceLeft(_ or _)

  // ---- FromServer

  import StreamingMessage.FromServer._

  implicit val EncoderConnectionError: Encoder[ConnectionError] =
    (a: ConnectionError) =>
      Json.obj(
        "type"    -> Json.fromString("connection_error"),
        "payload" -> a.payload
      )

  implicit val DecoderConnectionError: Decoder[ConnectionError] =
    (c: HCursor) =>
      for {
        _ <- checkType(c, "connection_error")
        p <- c.downField("payload").as[Json]
      } yield ConnectionError(p)

  implicit val EncoderDataWrapper: Encoder[DataWrapper] =
    (a: DataWrapper) =>
      Json.obj(
        "data" -> a.data
      )

  implicit val DecoderDataWrapper: Decoder[DataWrapper] =
    (c: HCursor) => c.downField("data").as[Json].map(DataWrapper(_))

  implicit val EncoderData: Encoder[Data]               =
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
    List[Decoder[StreamingMessage.FromServer]](
      decodeCaseObject("connection_ack", ConnectionAck),
      Decoder[ConnectionError].widen,
      decodeCaseObject("ka", ConnectionKeepAlive),
      Decoder[Data].widen,
      Decoder[Error].widen,
      Decoder[Complete].widen
    ).reduceLeft(_ or _)

  private def checkType(c: HCursor, expected: String): Decoder.Result[Unit] =
    c.downField("type")
      .as[String]
      .filterOrElse(_ === expected, DecodingFailure(s"expected '$expected''", c.history))
      .void

  private def encodeCaseObject(tpe:            String): Json =
    Json.obj("type" -> Json.fromString(tpe))

  private def decodeCaseObject[A, W >: A](tpe: String, instance: A): Decoder[W] =
    Decoder.instance[A](c => checkType(c, tpe).as(instance)).widen[W]

}
