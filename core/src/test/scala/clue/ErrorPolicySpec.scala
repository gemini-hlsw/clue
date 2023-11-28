// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.data.Ior
import cats.data.NonEmptyList
import clue.model.GraphQLError
import clue.model.GraphQLResponse
import org.scalacheck.Arbitrary
import org.typelevel.log4cats.testing.StructuredTestingLogger
import cats.effect.IO
import cats.syntax.all.*

class ErrorPolicySpec extends munit.CatsEffectSuite {

  type ThrowOr[+A] = Either[Throwable, A]

  val graphQlError = Arbitrary.arbString.arbitrary.map(GraphQLError(_)).sample.get

  private val loggerFixture =
    FunFixture[StructuredTestingLogger[IO]](_ => StructuredTestingLogger.impl[IO](), _ => ())

  loggerFixture.test("IgnoreOnData only errors") { implicit logger =>
    val policy = ErrorPolicy.IgnoreOnData.processor[Int]

    val result =
      policy.process[IO](GraphQLResponse(result = Ior.left(NonEmptyList.one(graphQlError))))

    result.intercept[ResponseException[Int]] *>
      logger.logged.assert(_.isEmpty)
  }

  loggerFixture.test("IgnoreOnData only data") { implicit logger =>
    val policy = ErrorPolicy.IgnoreOnData.processor[Int]

    val result =
      policy.process[IO](GraphQLResponse(result = Ior.Right(1)))

    result.assertEquals(1) *>
      logger.logged.assert(_.isEmpty)
  }

  loggerFixture.test("IgnoreOnData errors and data") { implicit logger =>
    val policy = ErrorPolicy.IgnoreOnData.processor[Int]

    val result =
      policy.process[IO](GraphQLResponse(result = Ior.both(NonEmptyList.one(graphQlError), 1)))

    result.assertEquals(1) *>
      logger.logged.assertEquals(
        Vector(
          StructuredTestingLogger.ERROR(
            "Received both data and errors",
            throwOpt = ResponseException(NonEmptyList.one(graphQlError), 1.some).some
          )
        )
      )
  }
}
