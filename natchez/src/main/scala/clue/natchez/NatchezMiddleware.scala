// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.natchez

import cats.Applicative
import cats.effect.MonadCancelThrow
import cats.effect.Resource
import cats.syntax.all.*
import clue.FetchClientWithPars
import clue.StreamingClient
import clue.model.GraphQLQuery
import clue.model.GraphQLResponse
import io.circe.Decoder
import io.circe.JsonObject
import natchez.Span
import natchez.Span.Options.Defaults
import natchez.Span.SpanKind
import natchez.Trace
import natchez.TraceValue

object NatchezMiddleware:
  def apply[F[_]: Trace: MonadCancelThrow, P, S](
    client:                FetchClientWithPars[F, P, S],
    spanOptions:           Span.Options,
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[Seq[(String, TraceValue)]]
  ): FetchClientWithPars[F, P, S] =
    NatchezFetchClient[F, P, S](client, spanOptions, additionalAttributesF)

  def apply[F[_]: Trace: MonadCancelThrow, P, S](
    client:                FetchClientWithPars[F, P, S],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[Seq[(String, TraceValue)]]
  ): FetchClientWithPars[F, P, S] =
    apply(client, Defaults.withSpanKind(SpanKind.Client), additionalAttributesF)

  def withAttributes[F[_]: Trace: MonadCancelThrow, P, S](
    client:               FetchClientWithPars[F, P, S],
    spanOptions:          Span.Options
  )(
    additionalAttributes: (String, TraceValue)*
  ): FetchClientWithPars[F, P, S] =
    apply(
      client,
      spanOptions,
      (_: GraphQLQuery, _: Option[JsonObject]) => additionalAttributes.pure[F]
    )

  def withAttributes[F[_]: Trace: MonadCancelThrow, P, S](
    client:               FetchClientWithPars[F, P, S]
  )(
    additionalAttributes: (String, TraceValue)*
  ): FetchClientWithPars[F, P, S] =
    apply(
      client,
      (_: GraphQLQuery, _: Option[JsonObject]) => additionalAttributes.pure[F]
    )

  def apply[F[_]: Trace: MonadCancelThrow, P, S](
    client: FetchClientWithPars[F, P, S]
  ): FetchClientWithPars[F, P, S] =
    apply(
      client,
      (_: GraphQLQuery, _: Option[JsonObject]) => Seq.empty[(String, TraceValue)].pure[F]
    )

  def apply[F[_]: Trace: MonadCancelThrow, S](
    client:                StreamingClient[F, S],
    spanOptions:           Span.Options,
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[Seq[(String, TraceValue)]]
  ): StreamingClient[F, S] =
    NatchezStreamingClient(client, spanOptions, additionalAttributesF)

  def apply[F[_]: Trace: MonadCancelThrow, S](
    client:                StreamingClient[F, S],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[Seq[(String, TraceValue)]]
  ): StreamingClient[F, S] =
    apply(client, Defaults.withSpanKind(SpanKind.Client), additionalAttributesF)

  def withAttributes[F[_]: Trace: MonadCancelThrow, P, S](
    client:      StreamingClient[F, S],
    spanOptions: Span.Options
  )(additionalAttributes: (String, TraceValue)*): StreamingClient[F, S] =
    apply(
      client,
      spanOptions,
      (_: GraphQLQuery, _: Option[JsonObject]) => additionalAttributes.pure[F]
    )

  def withAttributes[F[_]: Trace: MonadCancelThrow, P, S](
    client: StreamingClient[F, S]
  )(additionalAttributes: (String, TraceValue)*): StreamingClient[F, S] =
    apply(
      client,
      (_: GraphQLQuery, _: Option[JsonObject]) => additionalAttributes.pure[F]
    )

  def apply[F[_]: Trace: MonadCancelThrow, S](
    client: StreamingClient[F, S]
  ): StreamingClient[F, S] =
    apply(
      client,
      (_: GraphQLQuery, _: Option[JsonObject]) => Seq.empty[(String, TraceValue)].pure[F]
    )

class NatchezFetchClient[F[_]: Trace: MonadCancelThrow, P, S](
  wrapped:               FetchClientWithPars[F, P, S],
  spanOptions:           Span.Options,
  additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[Seq[(String, TraceValue)]]
) extends FetchClientWithPars[F, P, S]:
  override protected[clue] def requestInternal[D: Decoder](
    document:      GraphQLQuery,
    operationName: Option[String],
    variables:     Option[JsonObject],
    modParams:     P => P
  ): F[GraphQLResponse[D]] =
    MonadCancelThrow[F].uncancelable: poll =>
      Trace[F].span(s"clue-client-request-${document.querySummary}", spanOptions):
        for
          additionalAttributes <- additionalAttributesF(document, variables)
          _                    <- Trace[F].put(additionalAttributes*)
          result               <- poll:
                                    wrapped.requestInternal[D](document, operationName, variables, modParams)
          _                    <- Trace[F].put:
                                    "clue.response.hasData" -> result.data.isDefined
          _                    <- result.errors.fold(Applicative[F].unit): errs =>
                                    Trace[F].put:
                                      "clue.response.errors" -> errs.toList.mkString("[", ", ", "]")
        yield result

// Extension methods for convenient tracing
object http4s:
  extension [F[_], P, S](client: F[FetchClientWithPars[F, P, S]]) {
    @scala.annotation.targetName("tracedFetchClient")
    def traced(using
      cats.effect.MonadCancelThrow[F],
      natchez.Trace[F],
      cats.Functor[F]
    ): F[FetchClientWithPars[F, P, S]] =
      client.map(NatchezMiddleware(_))

    @scala.annotation.targetName("tracedWithFetchClient")
    def tracedWith(
      spanOptions: natchez.Span.Options,
      additionalAttributesF: (clue.model.GraphQLQuery, Option[io.circe.JsonObject]) => F[Seq[(String, natchez.TraceValue)]]
    )(using
      cats.effect.MonadCancelThrow[F],
      natchez.Trace[F],
      cats.Functor[F]
    ): F[FetchClientWithPars[F, P, S]] =
      client.map(NatchezMiddleware(_, spanOptions, additionalAttributesF))
  }

  extension [F[_], S](client: F[StreamingClient[F, S]]) {
    @scala.annotation.targetName("tracedStreamingClient")
    def traced(using
      cats.effect.MonadCancelThrow[F],
      natchez.Trace[F],
      cats.Functor[F]
    ): F[StreamingClient[F, S]] =
      client.map(NatchezMiddleware(_))

    @scala.annotation.targetName("tracedWithStreamingClient")
    def tracedWith(
      spanOptions: natchez.Span.Options,
      additionalAttributesF: (clue.model.GraphQLQuery, Option[io.circe.JsonObject]) => F[Seq[(String, natchez.TraceValue)]]
    )(using
      cats.effect.MonadCancelThrow[F],
      natchez.Trace[F],
      cats.Functor[F]
    ): F[StreamingClient[F, S]] =
      client.map(NatchezMiddleware(_, spanOptions, additionalAttributesF))
  }

class NatchezStreamingClient[F[_]: Trace: MonadCancelThrow, S](
  wrapped:               StreamingClient[F, S],
  spanOptions:           Span.Options,
  additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[Seq[(String, TraceValue)]]
) extends NatchezFetchClient[F, Unit, S](wrapped, spanOptions, additionalAttributesF)
    with StreamingClient[F, S]:
  protected[clue] def subscribeInternal[D: Decoder](
    document:      GraphQLQuery,
    operationName: Option[String] = none,
    variables:     Option[JsonObject] = none
  ): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
    val resource: Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
      Resource.applyFull: poll =>
        Trace[F].span(s"clue-client-start-${document.querySummary}", spanOptions):
          for
            additionalAttributes <- additionalAttributesF(document, variables)
            _                    <- Trace[F].put(additionalAttributes*)
            result               <- poll:
                                      wrapped
                                        .subscribeInternal[D](document, operationName, variables)
                                        .allocatedCase
          yield result
    resource.onFinalizeCase: exitCase =>
      Trace[F].log(s"clue-client-end-${document.querySummary} ${exitCase.toOutcome}")
