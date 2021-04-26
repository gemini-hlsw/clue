// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import cats.syntax.all._
import cats.effect.IO
import scala.concurrent.ExecutionContext
import edu.gemini.grackle.Schema
import java.io.File
import scala.io.Source
import cats.effect.{ Deferred, Ref }

final case class GraphQLGenConfig(
  schemaDirs:        List[String] = List.empty,
  // schemaDirs:        String = "",
  catsEq:            Boolean = true,
  catsShow:          Boolean = true,
  monocleLenses:     Boolean = true,
  scalaJSReactReuse: Boolean = false,
  circeEncoder:      Boolean = true,
  circeDecoder:      Boolean = true
) {
  implicit private val csIO: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)

  private val schemas: Ref[IO, Map[String, Deferred[IO, Schema]]] = Ref.unsafe(Map.empty)

  /**
   * Parse the schema file.
   */
  private def retrieveSchema(schemaName: String): IO[Schema] = {
    val fileName = s"$schemaName.graphql"

    def findSchemaFile: IO[File] =
      IO(
        schemaDirs
          .collectFirstSome { dir =>
            val dirFile    = new File(dir)
            val schemaFile = new File(dirFile, fileName)

            // println(schemaFile.getAbsolutePath())

            Option(schemaFile).filter(_.exists)
          }
          .toRight(
            new Exception(s"No schema [$fileName] found in paths [${schemaDirs.mkString(", ")}]")
          )
      ).rethrow

    findSchemaFile.flatMap { file =>
      IO(Source.fromFile(file).getLines().mkString("\n")).flatMap { schemaString =>
        val schema = Schema(schemaString)
        if (schema.isLeft)
          abort(
            s"Could not parse schema at [$fileName]: ${schema.left.get.toChain.map(_.toString).toList.mkString("\n")}"
          )
        else
          IO.whenA(schema.isBoth)(
            log(
              s"Warning when parsing schema [$fileName]: ${schema.left.get.toChain.map(_.toString).toList.mkString("\n")}"
            )
          ) >>
            IO.pure(schema.right.get)
      }
    }
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
  def default          = GraphQLGenConfig()
  implicit val surface = metaconfig.generic.deriveSurface[GraphQLGenConfig]
  implicit val decoder = metaconfig.generic.deriveDecoder(default)
}
