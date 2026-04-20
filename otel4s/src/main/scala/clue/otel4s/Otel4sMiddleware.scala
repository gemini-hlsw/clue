// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.otel4s

import cats.Applicative
import cats.effect.Concurrent
import cats.effect.MonadCancelThrow
import cats.effect.Resource
import cats.syntax.all.*
import clue.BuildInfo
import clue.FetchClientWithPars
import clue.PersistentClientStatus
import clue.PersistentStreamingClient
import clue.StreamingClient
import clue.TraceHeaderInjector
import clue.model.GraphQLQuery
import clue.model.GraphQLResponse
import io.circe.Decoder
import io.circe.Encoder
import io.circe.JsonObject
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.context.propagation.TextMapUpdater
import org.typelevel.otel4s.semconv.attributes.HttpAttributes
import org.typelevel.otel4s.semconv.experimental.attributes.GraphqlExperimentalAttributes
import org.typelevel.otel4s.trace.Span
import org.typelevel.otel4s.trace.SpanBuilder
import org.typelevel.otel4s.trace.SpanKind
import org.typelevel.otel4s.trace.StatusCode
import org.typelevel.otel4s.trace.Tracer

object Otel4sMiddleware:
  import GraphqlExperimentalAttributes.GraphqlOperationTypeValue

  private[otel4s] def extractOperationType(query: String): Option[GraphqlOperationTypeValue] =
    val trimmed = query.trim.toLowerCase
    if trimmed.startsWith("query") then Some(GraphqlOperationTypeValue.Query)
    else if trimmed.startsWith("mutation") then Some(GraphqlOperationTypeValue.Mutation)
    else if trimmed.startsWith("subscription") then Some(GraphqlOperationTypeValue.Subscription)
    else None

  type SpanBuilderMod[F[_]] = SpanBuilder[F] => SpanBuilder[F]

  private def identityMod[F[_]]: SpanBuilderMod[F] = identity

  private def emptyAttrs[F[_]: Applicative]
    : (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]] =
    (_, _) => List.empty[Attribute[?]].pure[F]

  // Fetch client
  def apply[F[_]: Tracer: MonadCancelThrow, P: TraceHeaderInjector, S](
    client:                FetchClientWithPars[F, P, S],
    spanBuilderMod:        SpanBuilderMod[F],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
  ): FetchClientWithPars[F, P, S] =
    Otel4sFetchClient[F, P, S](client, spanBuilderMod, additionalAttributesF)

  def apply[F[_]: Tracer: MonadCancelThrow, P: TraceHeaderInjector, S](
    client: FetchClientWithPars[F, P, S]
  ): FetchClientWithPars[F, P, S] =
    apply(client, identityMod[F], emptyAttrs[F])

  // Streaming client
  def apply[F[_]: Tracer: Concurrent, S](
    client:                StreamingClient[F, S],
    spanBuilderMod:        SpanBuilderMod[F],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
  ): StreamingClient[F, S] =
    Otel4sStreamingClient(client, spanBuilderMod, additionalAttributesF)

  def apply[F[_]: Tracer: Concurrent, S](
    client: StreamingClient[F, S]
  ): StreamingClient[F, S] =
    apply(client, identityMod[F], emptyAttrs[F])

  // Persistent streaming client
  def apply[F[_]: Tracer: Concurrent, S, CP, CE](
    client:                PersistentStreamingClient[F, S, CP, CE],
    spanBuilderMod:        SpanBuilderMod[F],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
  ): PersistentStreamingClient[F, S, CP, CE] =
    Otel4sPersistentStreamingClient(client, spanBuilderMod, additionalAttributesF)

  def apply[F[_]: Tracer: Concurrent, S, CP, CE](
    client: PersistentStreamingClient[F, S, CP, CE]
  ): PersistentStreamingClient[F, S, CP, CE] =
    apply(client, identityMod[F], emptyAttrs[F])

  private[otel4s] def commonAttributes(
    document:      GraphQLQuery,
    operationName: Option[String]
  ): List[Attribute[?]] =
    val base   = List[Attribute[?]](
      Attribute("clue.version", BuildInfo.version),
      HttpAttributes.HttpRequestMethod("POST"),
      GraphqlExperimentalAttributes.GraphqlDocument(document.value)
    )
    val opType = extractOperationType(document.value)
      .map(t => GraphqlExperimentalAttributes.GraphqlOperationType(t.value))
      .toList
    val opName = operationName
      .map(n => GraphqlExperimentalAttributes.GraphqlOperationName(n))
      .toList
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

class Otel4sFetchClient[F[_]: Tracer: MonadCancelThrow, P: TraceHeaderInjector, S](
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
          additional   <- additionalAttributesF(document, variables)
          _            <- span.addAttributes(additional*)
          traceHeaders <- Tracer[F].propagate(Map.empty[String, String])
          modWithTrace  = modParams.andThen(p => TraceHeaderInjector[P].addHeaders(p, traceHeaders))
          result       <-
            poll(wrapped.requestInternal[D](document, operationName, variables, modWithTrace))
          _            <- Otel4sMiddleware.recordResponseAttributes(span, result)
        yield result

class Otel4sStreamingClient[F[_]: Tracer: Concurrent, S](
  wrapped:               StreamingClient[F, S],
  spanBuilderMod:        Otel4sMiddleware.SpanBuilderMod[F],
  additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
) extends Otel4sFetchClient[F, Unit, S](wrapped, spanBuilderMod, additionalAttributesF)
    with StreamingClient[F, S]:
  protected[clue] def subscribeInternal[D: Decoder](
    document:      GraphQLQuery,
    operationName: Option[String] = none,
    variables:     Option[JsonObject] = none,
    extensions:    Option[JsonObject] = none
  ): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
    for
      res          <- spanBuilderMod(
                        Tracer[F]
                          .spanBuilder(s"clue-client-subscribe-${document.querySummary}")
                          .withSpanKind(SpanKind.Client)
                          .addAttributes(Otel4sMiddleware.commonAttributes(document, operationName)*)
                      ).build.resource
      span          = res.span
      _            <- Resource.eval:
                        additionalAttributesF(document, variables).flatMap: attrs =>
                          span.addAttributes(attrs*)
      traceHeaders <- Resource.eval(Tracer[F].propagate(Map.empty[String, String]))
      traceExt      = JsonObject.fromMap(traceHeaders.map((k, v) => k -> io.circe.Json.fromString(v)))
      mergedExt     = extensions
                        .map(ext => JsonObject.fromIterable(ext.toIterable ++ traceExt.toIterable))
                        .orElse(Some(traceExt))
                        .filterNot(_.isEmpty)
      stream       <- wrapped.subscribeInternal[D](document, operationName, variables, mergedExt)
    yield stream.onFinalizeCase: exitCase =>
      span.addAttribute(Attribute("clue.exitCase", exitCase.toOutcome.toString)) *>
        (exitCase match
          case Resource.ExitCase.Errored(e) =>
            span.setStatus(StatusCode.Error, Option(e.getMessage).getOrElse(e.toString))
          case _                            => Applicative[F].unit)

  override protected[clue] def requestInternal[D: Decoder](
    document:      GraphQLQuery,
    operationName: Option[String],
    variables:     Option[JsonObject],
    modParams:     Unit => Unit
  ): F[GraphQLResponse[D]] =
    MonadCancelThrow[F].uncancelable: poll =>
      spanBuilderMod(
        Tracer[F]
          .spanBuilder(s"clue-client-request-${document.querySummary}")
          .withSpanKind(SpanKind.Client)
          .addAttributes(Otel4sMiddleware.commonAttributes(document, operationName)*)
      ).build.use: span =>
        for
          additional   <- additionalAttributesF(document, variables)
          _            <- span.addAttributes(additional*)
          traceHeaders <- Tracer[F].propagate(Map.empty[String, String])
          traceExt      =
            JsonObject.fromMap(traceHeaders.map((k, v) => k -> io.circe.Json.fromString(v)))
          mergedExt     = Some(traceExt).filterNot(_.isEmpty)
          result       <- poll(
                            wrapped
                              .subscribeInternal[D](document, operationName, variables, mergedExt)
                              .use(_.head.compile.onlyOrError)
                          )
          _            <- Otel4sMiddleware.recordResponseAttributes(span, result)
        yield result

class Otel4sPersistentStreamingClient[F[_]: Tracer: Concurrent, S, CP, CE](
  wrapped:               PersistentStreamingClient[F, S, CP, CE],
  spanBuilderMod:        Otel4sMiddleware.SpanBuilderMod[F],
  additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
) extends Otel4sStreamingClient[F, S](wrapped, spanBuilderMod, additionalAttributesF)
    with PersistentStreamingClient[F, S, CP, CE]:
  def status: F[PersistentClientStatus]                          = wrapped.status
  def statusStream: fs2.Stream[F, PersistentClientStatus]        = wrapped.statusStream
  def connect[A: Encoder.AsObject](payload: F[A]): F[JsonObject] = wrapped.connect(payload)
  def connect(): F[JsonObject]                                   = wrapped.connect()
  def disconnect(closeParameters:           CP): F[Unit]         = wrapped.disconnect(closeParameters)
  def disconnect(): F[Unit]                                      = wrapped.disconnect()
