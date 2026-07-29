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
import io.circe.Json
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

  private[otel4s] type SpanMod[F[_]] = SpanBuilder[F] => SpanBuilder[F]

  private def identityMod[F[_]]: SpanMod[F] = identity

  private def emptyAttrs[F[_]: Applicative]
    : (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]] = (_, _) => List.empty.pure

  // Fetch client
  def apply[F[_]: Tracer: MonadCancelThrow, P: TraceHeaderInjector, S](
    client:                FetchClientWithPars[F, P, S],
    spanMod:               SpanMod[F],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
  ): FetchClientWithPars[F, P, S] =
    Otel4sFetchClient[F, P, S](client, spanMod, additionalAttributesF)

  def apply[F[_]: Tracer: MonadCancelThrow, P: TraceHeaderInjector, S](
    client: FetchClientWithPars[F, P, S]
  ): FetchClientWithPars[F, P, S] =
    apply(client, identityMod[F], emptyAttrs[F])

  // Streaming client
  def apply[F[_]: Tracer: Concurrent, S](
    client:                StreamingClient[F, S],
    spanMod:               SpanMod[F],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
  ): StreamingClient[F, S] =
    Otel4sStreamingClient(client, spanMod, additionalAttributesF)

  def apply[F[_]: Tracer: Concurrent, S](
    client: StreamingClient[F, S]
  ): StreamingClient[F, S] =
    apply(client, identityMod[F], emptyAttrs[F])

  // Persistent streaming client
  def apply[F[_]: Tracer: Concurrent, S, CP, CE](
    client:                PersistentStreamingClient[F, S, CP, CE],
    spanMod:               SpanMod[F],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
  ): PersistentStreamingClient[F, S, CP, CE] =
    Otel4sPersistentStreamingClient(client, spanMod, additionalAttributesF)

  def apply[F[_]: Tracer: Concurrent, S, CP, CE](
    client: PersistentStreamingClient[F, S, CP, CE]
  ): PersistentStreamingClient[F, S, CP, CE] =
    apply(client, identityMod[F], emptyAttrs[F])

  private[otel4s] def commonAttributes(
    document:      GraphQLQuery,
    operationName: Option[String],
    descriptor:    Option[String]
  ): List[Attribute[?]] =
    val base = List(
      Attribute("clue.version", BuildInfo.version),
      GraphqlExperimentalAttributes.GraphqlDocument(document.value)
    )

    val opType = document.operationType
      .map(GraphqlExperimentalAttributes.GraphqlOperationType(_))
      .toList

    val opName = operationName
      .map(n => GraphqlExperimentalAttributes.GraphqlOperationName(n))
      .toList

    val descr = descriptor.map(d => Attribute("clue.descriptor", d)).toList

    base ++ opType ++ opName ++ descr

  private[otel4s] def responseAttributes[F[_]: Applicative as F, D](
    span:   Span[F],
    result: GraphQLResponse[D]
  ): F[Unit] =
    span.addAttribute(Attribute("clue.response.hasData", result.data.isDefined)) *>
      result.errors
        .map: errs =>
          val errorsStr = errs.toList.mkString("[", ", ", "]")
          span.addAttributes(
            Attribute("clue.response.hasErrors", true),
            Attribute("clue.response.errorCount", errs.length.toLong),
            Attribute("clue.response.errors", errorsStr)
          ) *> span.setStatus(StatusCode.Error, "GraphQL request returned errors")
        .getOrElse(F.unit)

class Otel4sFetchClient[F[_]: {MonadCancelThrow, Tracer as T}, P: TraceHeaderInjector, S](
  wrapped:               FetchClientWithPars[F, P, S],
  spanMod:               Otel4sMiddleware.SpanMod[F],
  additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
) extends FetchClientWithPars[F, P, S]:
  protected def traceSpan(
    operation:     String,
    document:      GraphQLQuery,
    operationName: Option[String],
    descriptor:    Option[String]
  ) = spanMod(
    T.spanBuilder(s"clue-$operation-${descriptor.getOrElse(document.querySummary)}")
      .withSpanKind(SpanKind.Client)
      .addAttributes(Otel4sMiddleware.commonAttributes(document, operationName, descriptor)*)
  )

  // Merge existing extensions with otel trace parent headers.
  protected def mergeOtelExtension(
    extensions:   Option[JsonObject],
    traceHeaders: Map[String, String]
  ): Option[JsonObject] =
    val traceExt = JsonObject.fromMap(traceHeaders.map((k, v) => k -> Json.fromString(v)))
    extensions.map(_.deepMerge(traceExt)).orElse(Some(traceExt)).filterNot(_.isEmpty)

  /**
   * Transport-specific attributes applied to the `request` span. The HTTP fetch client reports
   * `http.request.method: POST`.
   */
  protected[otel4s] def requestTransportAttributes: List[Attribute[?]] =
    List(HttpAttributes.HttpRequestMethod("POST"))

  override protected[clue] def requestInternal[D: Decoder](
    document:      GraphQLQuery,
    operationName: Option[String],
    variables:     Option[JsonObject],
    extensions:    Option[JsonObject],
    modParams:     P => P,
    descriptor:    Option[String] = none
  ): F[GraphQLResponse[D]] =
    MonadCancelThrow[F].uncancelable: poll =>
      traceSpan("request", document, operationName, descriptor)
        .addAttributes(requestTransportAttributes*)
        .build
        .use: span =>
          for
            additional   <- additionalAttributesF(document, variables)
            _            <- span.addAttributes(additional*)
            traceHeaders <- T.propagate(Map.empty)
            mergedExt     = mergeOtelExtension(extensions, traceHeaders)
            modWithTrace  =
              modParams.andThen(p => TraceHeaderInjector[P].addHeaders(p, traceHeaders))
            result       <-
              poll(
                wrapped
                  .requestInternal[D](
                    document,
                    operationName,
                    variables,
                    mergedExt,
                    modWithTrace,
                    descriptor
                  )
              )
            _            <- Otel4sMiddleware.responseAttributes(span, result)
          yield result

class Otel4sStreamingClient[F[_]: {Concurrent, Tracer as T}, S](
  wrapped:               StreamingClient[F, S],
  spanMod:               Otel4sMiddleware.SpanMod[F],
  additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
) extends Otel4sFetchClient[F, Unit, S](wrapped, spanMod, additionalAttributesF)
    with StreamingClient[F, S]:
  // Streaming clients run over a persistent transport (typically WebSocket), not HTTP
  override protected[otel4s] def requestTransportAttributes: List[Attribute[?]] = Nil

  protected[clue] def subscribeInternal[D: Decoder](
    document:      GraphQLQuery,
    operationName: Option[String] = none,
    variables:     Option[JsonObject] = none,
    extensions:    Option[JsonObject] = none,
    descriptor:    Option[String] = none
  ): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
    for
      res          <- traceSpan("subscribe", document, operationName, descriptor).build.resource
      span          = res.span
      _            <- Resource.eval:
                        additionalAttributesF(document, variables).flatMap: attrs =>
                          span.addAttributes(attrs*)
      traceHeaders <- Resource.eval(T.propagate(Map.empty))
      mergedExt     = mergeOtelExtension(extensions, traceHeaders)
      stream       <- wrapped.subscribeInternal[D](
                        document,
                        operationName,
                        variables,
                        mergedExt,
                        descriptor
                      )
    yield stream.onFinalizeCase: exitCase =>
      span.addAttribute(Attribute("clue.exitCase", exitCase.toOutcome.toString)) *>
        (exitCase match
          case Resource.ExitCase.Errored(e) =>
            span.setStatus(StatusCode.Error, Option(e.getMessage).getOrElse(e.toString))
          case _                            => Applicative[F].unit)

class Otel4sPersistentStreamingClient[F[_]: Tracer: Concurrent, S, CP, CE](
  wrapped:               PersistentStreamingClient[F, S, CP, CE],
  spanMod:               Otel4sMiddleware.SpanMod[F],
  additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
) extends Otel4sStreamingClient[F, S](wrapped, spanMod, additionalAttributesF)
    with PersistentStreamingClient[F, S, CP, CE]:
  def status: F[PersistentClientStatus]                          = wrapped.status
  def statusStream: fs2.Stream[F, PersistentClientStatus]        = wrapped.statusStream
  def connect[A: Encoder.AsObject](payload: F[A]): F[JsonObject] = wrapped.connect(payload)
  def connect(): F[JsonObject]                                   = wrapped.connect()
  def disconnect(closeParameters:           CP): F[Unit]         = wrapped.disconnect(closeParameters)
  def disconnect(): F[Unit]                                      = wrapped.disconnect()
