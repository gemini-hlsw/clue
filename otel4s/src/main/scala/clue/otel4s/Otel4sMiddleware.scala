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
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.trace.StatusCode

object Otel4sMiddleware:
  private[otel4s] def extractOperationType(query: String): Option[String] =
    val trimmed = query.trim.toLowerCase
    if trimmed.startsWith("query") then Some("query")
    else if trimmed.startsWith("mutation") then Some("mutation")
    else if trimmed.startsWith("subscription") then Some("subscription")
    else None
  def apply[F[_]: Tracer: MonadCancelThrow, P, S](
    client:                FetchClientWithPars[F, P, S],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
  ): FetchClientWithPars[F, P, S] =
    Otel4sFetchClient[F, P, S](client, additionalAttributesF)

  def apply[F[_]: Tracer: MonadCancelThrow, P, S](
    client: FetchClientWithPars[F, P, S]
  ): FetchClientWithPars[F, P, S] =
    apply(client, (_: GraphQLQuery, _: Option[JsonObject]) => List.empty[Attribute[?]].pure[F])

  def withAttributes[F[_]: Tracer: MonadCancelThrow, P, S](
    client: FetchClientWithPars[F, P, S]
  )(
    additionalAttributes: Attribute[?]*
  ): FetchClientWithPars[F, P, S] =
    apply(
      client,
      (_: GraphQLQuery, _: Option[JsonObject]) => additionalAttributes.toList.pure[F]
    )

  def apply[F[_]: Tracer: MonadCancelThrow, S](
    client:                StreamingClient[F, S],
    additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
  ): StreamingClient[F, S] =
    Otel4sStreamingClient(client, additionalAttributesF)

  def apply[F[_]: Tracer: MonadCancelThrow, S](
    client: StreamingClient[F, S]
  ): StreamingClient[F, S] =
    apply(client, (_: GraphQLQuery, _: Option[JsonObject]) => List.empty[Attribute[?]].pure[F])

  def withAttributes[F[_]: Tracer: MonadCancelThrow, P, S](
    client: StreamingClient[F, S]
  )(additionalAttributes: Attribute[?]*): StreamingClient[F, S] =
    apply(
      client,
      (_: GraphQLQuery, _: Option[JsonObject]) => additionalAttributes.toList.pure[F]
    )

// Extension methods for convenient tracing
object http4s:
  extension [F[_], P, S](client: F[FetchClientWithPars[F, P, S]]) {
    @scala.annotation.targetName("tracedFetchClient")
    def traced(using
      org.typelevel.otel4s.trace.Tracer[F],
      cats.effect.MonadCancelThrow[F],
      cats.Functor[F]
    ): F[FetchClientWithPars[F, P, S]] =
      client.map(Otel4sMiddleware(_))

    @scala.annotation.targetName("tracedWithFetchClient")
    def tracedWith(
      additionalAttributesF: (clue.model.GraphQLQuery, Option[io.circe.JsonObject]) => F[List[org.typelevel.otel4s.Attribute[?]]]
    )(using
      org.typelevel.otel4s.trace.Tracer[F],
      cats.effect.MonadCancelThrow[F],
      cats.Functor[F]
    ): F[FetchClientWithPars[F, P, S]] =
      client.map(Otel4sMiddleware(_, additionalAttributesF))
  }

  extension [F[_], S](client: F[StreamingClient[F, S]]) {
    @scala.annotation.targetName("tracedStreamingClient")
    def traced(using
      org.typelevel.otel4s.trace.Tracer[F],
      cats.effect.MonadCancelThrow[F],
      cats.Functor[F]
    ): F[StreamingClient[F, S]] =
      client.map(Otel4sMiddleware(_))

    @scala.annotation.targetName("tracedWithStreamingClient")
    def tracedWith(
      additionalAttributesF: (clue.model.GraphQLQuery, Option[io.circe.JsonObject]) => F[List[org.typelevel.otel4s.Attribute[?]]]
    )(using
      org.typelevel.otel4s.trace.Tracer[F],
      cats.effect.MonadCancelThrow[F],
      cats.Functor[F]
    ): F[StreamingClient[F, S]] =
      client.map(Otel4sMiddleware(_, additionalAttributesF))
  }

class Otel4sFetchClient[F[_]: Tracer: MonadCancelThrow, P, S](
  wrapped:               FetchClientWithPars[F, P, S],
  additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
) extends FetchClientWithPars[F, P, S]:
  override protected[clue] def requestInternal[D: Decoder](
    document:      GraphQLQuery,
    operationName: Option[String],
    variables:     Option[JsonObject],
    modParams:     P => P
  ): F[GraphQLResponse[D]] =
    MonadCancelThrow[F].uncancelable: poll =>
      Tracer[F].span(s"clue-client-request-${document.querySummary}").use: span =>
        for
          // Add clue library version
          _ <- span.addAttribute(Attribute("clue.version", BuildInfo.version))
          // Add standard OpenTelemetry semantic convention attributes
          _ <- span.addAttribute(Attribute("http.request.method", "POST"))
          _ <- Otel4sMiddleware.extractOperationType(document.value).traverse(opType =>
                 span.addAttribute(Attribute("graphql.operation.type", opType)))
          _ <- operationName.traverse(name => span.addAttribute(Attribute("graphql.operation.name", name)))
          _ <- span.addAttribute(Attribute("graphql.document", document.value))
          // Add user-provided additional attributes
          additionalAttributes <- additionalAttributesF(document, variables)
          _                    <- additionalAttributes.traverse(span.addAttribute)
          result               <- poll:
                                    wrapped.requestInternal[D](document, operationName, variables, modParams)
          // Add response attributes and handle errors
          _                    <- span.addAttribute:
                                    Attribute("clue.response.hasData", result.data.isDefined)
          _                    <- result.errors.fold(Applicative[F].unit): errs =>
                                    for
                                      _ <- span.addAttribute(Attribute("clue.response.hasErrors", true))
                                      _ <- span.addAttribute(Attribute("clue.response.errorCount", errs.length.toLong))
                                      _ <- span.addAttribute(Attribute("clue.response.errors", errs.toList.mkString("[", ", ", "]")))
                                      _ <- span.setStatus(StatusCode.Error, "GraphQL request returned errors")
                                    yield ()
        yield result

class Otel4sStreamingClient[F[_]: Tracer: MonadCancelThrow, S](
  wrapped:               StreamingClient[F, S],
  additionalAttributesF: (GraphQLQuery, Option[JsonObject]) => F[List[Attribute[?]]]
) extends Otel4sFetchClient[F, Unit, S](wrapped, additionalAttributesF)
    with StreamingClient[F, S]:
  protected[clue] def subscribeInternal[D: Decoder](
    document:      GraphQLQuery,
    operationName: Option[String] = none,
    variables:     Option[JsonObject] = none
  ): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
    val resource: Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
      Resource.applyFull: poll =>
        Tracer[F].span(s"clue-client-start-${document.querySummary}").use: span =>
          for
            // Add clue library version
            _ <- span.addAttribute(Attribute("clue.version", BuildInfo.version))
            // Add standard OpenTelemetry semantic convention attributes
            _ <- span.addAttribute(Attribute("http.request.method", "POST"))
            _ <- Otel4sMiddleware.extractOperationType(document.value).traverse(opType =>
                   span.addAttribute(Attribute("graphql.operation.type", opType)))
            _ <- operationName.traverse(name => span.addAttribute(Attribute("graphql.operation.name", name)))
            _ <- span.addAttribute(Attribute("graphql.document", document.value))
            // Add user-provided additional attributes
            additionalAttributes <- additionalAttributesF(document, variables)
            _                    <- additionalAttributes.traverse(span.addAttribute)
            result               <- poll:
                                      wrapped
                                        .subscribeInternal[D](document, operationName, variables)
                                        .allocatedCase
          yield result
    resource.onFinalizeCase: exitCase =>
      Tracer[F].span(s"clue-client-end-${document.querySummary}").use: span =>
        span.addAttribute(Attribute("clue.exitCase", exitCase.toOutcome.toString))