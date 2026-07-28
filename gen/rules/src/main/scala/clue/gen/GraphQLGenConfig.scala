// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.effect.kernel.Resource
import cats.syntax.all.*
import grackle.Result
import grackle.Schema
import metaconfig.ConfDecoder
import metaconfig.generic.Surface

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import scala.io.Source

final case class GraphQLGenConfig(
  schemaDirs:        List[String] = List.empty,
  catsEq:            Boolean = true,
  catsShow:          Boolean = true,
  monocleLenses:     Boolean = true,
  scalaJsReactReuse: Boolean = false,
  circeEncoder:      Boolean = true,
  circeDecoder:      Boolean = true,
  descriptor:        Boolean = false
) {
  // We memoize the [[Result]] of loading each schema. The Result carries everything: a failure
  // (missing or unparseable schema), warnings (a schema that parses with problems), or success.
  // Caching failures too is important: callers now recover from load failures (reporting them as
  // diagnostics) and keep going, so a second request for a schema that failed to load must not
  // block on a latch that would never be completed.
  private val schemas: Ref[IO, Map[String, Deferred[IO, Result[Schema]]]] =
    Ref.unsafe(Map.empty)

  /**
   * Parse the schema file. The returned [[Result]] accumulates load and parse problems (including
   * warnings) instead of throwing, so callers can surface them as diagnostics.
   */
  private def retrieveSchema(schemaName: String): IO[Result[Schema]] = {
    val fileName = s"$schemaName.graphql"

    val findSchemaStream: IO[Either[String, InputStream]] =
      IO(
        schemaDirs
          .collectFirstSome { dir =>
            val dirFile    = new File(dir)
            val schemaFile = new File(dirFile, fileName)

            // Attempt to open with ClassLoader. If it fails, go directly to file system.
            Option(getClass.getResourceAsStream(schemaFile.getPath))
              .orElse(
                Option(schemaFile).filter(_.exists).map(f => new FileInputStream(f))
              )
          }
          .toRight(s"No schema [$fileName] found in paths [${schemaDirs.mkString(", ")}]")
      )

    findSchemaStream.flatMap {
      case Left(message) => IO.pure(Result.failure(message))
      case Right(stream) =>
        Resource
          .fromAutoCloseable(IO(Source.fromInputStream(stream)))
          .use(source => IO(source.getLines().mkString("\n")))
          .map(Schema(_))
    }
  }

  def getSchema(name: String): IO[Result[Schema]] =
    Deferred[IO, Result[Schema]].flatMap { newLatch =>
      schemas
        .modify(map =>
          map.get(name) match {
            case Some(result) =>
              map -> result.get
            case None         =>
              (map + (name -> newLatch)) ->
                // `handleError` turns an unexpected I/O exception into a Result so the latch is
                // always completed (a never-completed latch would deadlock later requests).
                retrieveSchema(name).handleError(Result.internalError(_)).flatTap(newLatch.complete)
          }
        )
        .flatten
    }
}

object GraphQLGenConfig {
  def default: GraphQLGenConfig                       = GraphQLGenConfig()
  implicit val surface: Surface[GraphQLGenConfig]     =
    metaconfig.generic.deriveSurface[GraphQLGenConfig]
  implicit val decoder: ConfDecoder[GraphQLGenConfig] = metaconfig.generic.deriveDecoder(default)
}
