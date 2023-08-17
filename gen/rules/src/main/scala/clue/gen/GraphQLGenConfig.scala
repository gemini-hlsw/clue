// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.effect.kernel.Resource
import cats.syntax.all._
import edu.gemini.grackle.Schema
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
  scalaJSReactReuse: Boolean = false,
  circeEncoder:      Boolean = true,
  circeDecoder:      Boolean = true
) {
  private val schemas: Ref[IO, Map[String, Deferred[IO, Schema]]] = Ref.unsafe(Map.empty)

  /**
   * Parse the schema file.
   */
  private def retrieveSchema(schemaName: String): IO[Schema] = {
    val fileName = s"$schemaName.graphql"

    val findSchemaStream: IO[InputStream] =
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
          .toRight(
            new Exception(s"No schema [$fileName] found in paths [${schemaDirs.mkString(", ")}]")
          )
      ).rethrow

    findSchemaStream.flatMap(stream =>
      Resource
        .fromAutoCloseable(IO(Source.fromInputStream(stream)))
        .use(source => IO(source.getLines().mkString("\n")))
        .flatMap { schemaString =>
          val schema = Schema(schemaString)

          if (!schema.hasValue)
            abort(
              s"Could not parse schema at [$fileName]: ${schema.toProblems.map(_.toString).toList.mkString("\n")}"
            )
          else
            IO.whenA(schema.hasProblems)(
              log(
                s"Warning when parsing schema [$fileName]: ${schema.toProblems.map(_.toString).toList.mkString("\n")}"
              )
            ) >>
              IO.pure(schema.toOption.get)
        }
    )
  }

  def getSchema(name: String): IO[Schema] =
    Deferred[IO, Schema].flatMap { newLatch =>
      schemas
        .modify(map =>
          map.get(name) match {
            case Some(schema) =>
              map -> schema.get
            case None         =>
              (map + (name -> newLatch)) ->
                retrieveSchema(name).flatTap(newLatch.complete)
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
