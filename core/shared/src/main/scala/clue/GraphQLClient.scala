package clue

import cats.implicits._
import io.circe._
import io.circe.syntax._
import io.lemonlabs.uri.Url

trait GraphQLClient[F[_]] {
  // Query with GraphQLQuery
  def query(
    graphQLQuery:  GraphQLQuery,
    operationName: Option[String] = None
  )(variables:     Option[graphQLQuery.Variables] = None): F[graphQLQuery.Data] = {
    import graphQLQuery._

    queryInternal(graphQLQuery.document, operationName, variables.map(_.asJson))
  }

  // Queries with String
  def query[V: Encoder, D: Decoder](
    document:      String,
    variables:     V,
    operationName: String
  ): F[D] =
    queryInternal[D](document, operationName.some, variables.asJson.some)

  def query[D: Decoder](document: String, operationName: String): F[D] =
    queryInternal[D](document, operationName.some)

  def query[V: Encoder, D: Decoder](document: String, variables: V): F[D] =
    queryInternal[D](document, None, variables.asJson.some)

  def query[D: Decoder](document: String): F[D] =
    queryInternal[D](document)

  protected def queryInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[D]
}
