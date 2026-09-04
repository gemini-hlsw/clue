// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

/**
 * Media types and response rules of the GraphQL-over-HTTP specification.
 *
 * See https://github.com/graphql/graphql-over-http/blob/main/spec/GraphQLOverHTTP.md
 */
object GraphQLOverHttp:

  /** The media type for GraphQL responses. */
  val GraphQLResponseMediaType: String = "application/graphql-response+json"

  /**
   * Extract the media type from the value of a `Content-Type` header. The result has no parameters
   * and is in lower case.
   */
  def mediaType(contentType: Option[String]): Option[String] =
    contentType.map(_.takeWhile(_ != ';').trim.toLowerCase)

  /**
   * Decide if the client must read the body of an HTTP response as a GraphQL response.
   *
   * With the media type `application/graphql-response+json`, the body is a GraphQL response for
   * every status code. With any other media type, an intermediary such as a proxy can be the source
   * of the response, so the client trusts the body only on a 2xx status.
   */
  def processBody(status: Int, contentType: Option[String]): Boolean =
    mediaType(contentType).contains(GraphQLResponseMediaType) ||
      (status >= 200 && status < 300)
