// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.otel4s

import cats.Applicative
import cats.effect.MonadCancelThrow
import cats.effect.Resource
import cats.syntax.all.*
import clue.BuildInfo
import clue.FetchClientWithPars
import clue.StreamingClient
import clue.model.GraphQLQuery
import clue.model.GraphQLResponse
import io.circe.Decoder
import io.circe.JsonObject
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.Span
import org.typelevel.otel4s.trace.SpanBuilder
import org.typelevel.otel4s.trace.SpanKind
import org.typelevel.otel4s.trace.StatusCode
import org.typelevel.otel4s.trace.Tracer

object Otel4sMiddleware:
  private[otel4s] def extractOperationType(query: String): Option[String] =
    val trimmed = query.trim.toLowerCase
    if trimmed.startsWith("query") then Some("query")
    else if trimmed.startsWith("mutation") then Some("mutation")
    else if trimmed.startsWith("subscription") then Some("subscription")
    else None

  type SpanBuilderMod[F[_]] = SpanBuilder[F] => SpanBuilder[F]

  private def identityMod[F[_]]: SpanBuilderMod[F] = identity

  private def emptyAttrs[F[_]: Applicative]
    : (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]] =
    (_, _) => List.empty[Attribute[?]].pure[F]

  // Fetch client overloads
  def apply[F[_]: Tracer: MonadCancelThrow, P, S](
    client:                FetchClientWithPars[F, P, S],
    spanBuilderMod:        SpanBuilderMod[F],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
  ): FetchClientWithPars[F, P, S] =
    Otel4sFetchClient[F, P, S](client, spanBuilderMod, additionalAttributesF)

  def apply[F[_]: Tracer: MonadCancelThrow, P, S](
    client:                FetchClientWithPars[F, P, S],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
  ): FetchClientWithPars[F, P, S] =
    apply(client, identityMod[F], additionalAttributesF)

  def apply[F[_]: Tracer: MonadCancelThrow, P, S](
    client: FetchClientWithPars[F, P, S]
  ): FetchClientWithPars[F, P, S] =
    apply(client, identityMod[F], emptyAttrs[F])

  def withAttributes[F[_]: Tracer: MonadCancelThrow, P, S](
    client:         FetchClientWithPars[F, P, S],
    spanBuilderMod: SpanBuilderMod[F]
  )(additionalAttributes: Attribute[?]*): FetchClientWithPars[F, P, S] =
    apply(client, spanBuilderMod, (_, _) => additionalAttributes.toList.pure[F])

  def withAttributes[F[_]: Tracer: MonadCancelThrow, P, S](
    client: FetchClientWithPars[F, P, S]
  )(additionalAttributes: Attribute[?]*): FetchClientWithPars[F, P, S] =
    apply(client, identityMod[F], (_, _) => additionalAttributes.toList.pure[F])

  // Streaming client overloads

  def apply[F[_]: Tracer: MonadCancelThrow, S](
    client:                StreamingClient[F, S],
    spanBuilderMod:        SpanBuilderMod[F],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
  ): StreamingClient[F, S] =
    Otel4sStreamingClient(client, spanBuilderMod, additionalAttributesF)

  def apply[F[_]: Tracer: MonadCancelThrow, S](
    client:                StreamingClient[F, S],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
  ): StreamingClient[F, S] =
    apply(client, identityMod[F], additionalAttributesF)

  def apply[F[_]: Tracer: MonadCancelThrow, S](
    client: StreamingClient[F, S]
  ): StreamingClient[F, S] =
    apply(client, identityMod[F], emptyAttrs[F])

  def withAttributes[F[_]: Tracer: MonadCancelThrow, S](
    client:         StreamingClient[F, S],
    spanBuilderMod: SpanBuilderMod[F]
  )(additionalAttributes: Attribute[?]*): StreamingClient[F, S] =
    apply(client, spanBuilderMod, (_, _) => additionalAttributes.toList.pure[F])

  def withAttributes[F[_]: Tracer: MonadCancelThrow, S](
    client: StreamingClient[F, S]
  )(additionalAttributes: Attribute[?]*): StreamingClient[F, S] =
    apply(client, identityMod[F], (_, _) => additionalAttributes.toList.pure[F])

  private[otel4s] def commonAttributes(
    document:      GraphQLQuery,
    operationName: Option[String]
  ): List[Attribute[?]] =
    val base   = List[Attribute[?]](
      Attribute("clue.version", BuildInfo.version),
      Attribute("http.request.method", "POST"),
      Attribute("graphql.document", document.value)
    )
    val opType = extractOperationType(document.value)
      .map(t => Attribute("graphql.operation.type", t))
      .toList
    val opName = operationName.map(n => Attribute("graphql.operation.name", n)).toList
    base ++ opType ++ opName

  private[otel4s] def recordResponseAttributes[F[_]: Applicative, D](
    span:   Span[F],
    result: GraphQLResponse[D]
  ): F[Unit] =
    span.addAttribute(Attribute("clue.response.hasData", result.data.isDefined)) *>
      result.errors.fold(Applicative[F].unit): errs =>
        val errorsStr = errs.toList.mkString("[", ", ", "]")
        span.addAttributes(
          Attribute("clue.response.hasErrors", true),
          Attribute("clue.response.errorCount", errs.length.toLong),
          Attribute("clue.response.errors", errorsStr)
        ) *> span.setStatus(StatusCode.Error, "GraphQL request returned errors")

object http4s:
  import Otel4sMiddleware.SpanBuilderMod

  extension [F[_], P, S](client: F[FetchClientWithPars[F, P, S]]) {
    @scala.annotation.targetName("tracedFetchClient")
    def traced(using
      Tracer[F],
      MonadCancelThrow[F],
      cats.Functor[F]
    ): F[FetchClientWithPars[F, P, S]] =
      client.map(Otel4sMiddleware(_))

    @scala.annotation.targetName("tracedWithFetchClient")
    def tracedWith(
      spanBuilderMod:        SpanBuilderMod[F],
      additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
    )(using
      Tracer[F],
      MonadCancelThrow[F],
      cats.Functor[F]
    ): F[FetchClientWithPars[F, P, S]] =
      client.map(Otel4sMiddleware(_, spanBuilderMod, additionalAttributesF))
  }

  extension [F[_], S](client: F[StreamingClient[F, S]]) {
    @scala.annotation.targetName("tracedStreamingClient")
    def traced(using
      Tracer[F],
      MonadCancelThrow[F],
      cats.Functor[F]
    ): F[StreamingClient[F, S]] =
      client.map(Otel4sMiddleware(_))

    @scala.annotation.targetName("tracedWithStreamingClient")
    def tracedWith(
      spanBuilderMod:        SpanBuilderMod[F],
      additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
    )(using
      Tracer[F],
      MonadCancelThrow[F],
      cats.Functor[F]
    ): F[StreamingClient[F, S]] =
      client.map(Otel4sMiddleware(_, spanBuilderMod, additionalAttributesF))
  }

class Otel4sFetchClient[F[_]: Tracer: MonadCancelThrow, P, S](
  wrapped:               FetchClientWithPars[F, P, S],
  spanBuilderMod:        Otel4sMiddleware.SpanBuilderMod[F],
  additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
) extends FetchClientWithPars[F, P, S]:
  override protected[clue] def requestInternal[D: Decoder](
    document:      GraphQLQuery,
    operationName: Option[String],
    variables:     Option[JsonObject],
    modParams:     P => P
  ): F[GraphQLResponse[D]] =
    MonadCancelThrow[F].uncancelable: poll =>
      spanBuilderMod(
        Tracer[F]
          .spanBuilder(s"clue-client-request-${document.querySummary}")
          .withSpanKind(SpanKind.Client)
          .addAttributes(Otel4sMiddleware.commonAttributes(document, operationName)*)
      ).build.use: span =>
        for
          additional <- additionalAttributesF(document, variables)
          _          <- span.addAttributes(additional*)
          result     <- poll(wrapped.requestInternal[D](document, operationName, variables, modParams))
          _          <- Otel4sMiddleware.recordResponseAttributes(span, result)
        yield result

class Otel4sStreamingClient[F[_]: Tracer: MonadCancelThrow, S](
  wrapped:               StreamingClient[F, S],
  spanBuilderMod:        Otel4sMiddleware.SpanBuilderMod[F],
  additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
) extends Otel4sFetchClient[F, Unit, S](wrapped, spanBuilderMod, additionalAttributesF)
    with StreamingClient[F, S]:
  protected[clue] def subscribeInternal[D: Decoder](
    document:      GraphQLQuery,
    operationName: Option[String] = none,
    variables:     Option[JsonObject] = none
  ): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
    for
      res    <- spanBuilderMod(
                  Tracer[F]
                    .spanBuilder(s"clue-client-subscribe-${document.querySummary}")
                    .withSpanKind(SpanKind.Client)
                    .addAttributes(Otel4sMiddleware.commonAttributes(document, operationName)*)
                ).build.resource
      span    = res.span
      _      <- Resource.eval:
                  additionalAttributesF(document, variables).flatMap: attrs =>
                    span.addAttributes(attrs*)
      stream <- wrapped.subscribeInternal[D](document, operationName, variables)
    yield stream.onFinalizeCase: exitCase =>
      span.addAttribute(Attribute("clue.exitCase", exitCase.toOutcome.toString)) *>
        (exitCase match
          case Resource.ExitCase.Errored(e) =>
            span.setStatus(StatusCode.Error, Option(e.getMessage).getOrElse(e.toString))
          case _                            => Applicative[F].unit)
