// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.data.Ior
import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all.*
import clue.model.GraphQLDataResponse
import clue.model.GraphQLError
import clue.model.GraphQLResponse
import org.scalacheck.Arbitrary
import org.typelevel.log4cats.testing.StructuredTestingLogger

class ErrorPolicySpec extends munit.CatsEffectSuite {

  private val loggerFixture =
    FunFixture[StructuredTestingLogger[IO]](_ => StructuredTestingLogger.impl[IO](), _ => ())

  loggerFixture.test("IgnoreOnData only errors") { implicit logger =>
    val policy = ErrorPolicy.IgnoreOnData.processor[Int]

    val result = policy.process[IO](leftResponse)

    result.intercept[ResponseException[Int]] *>
      logger.logged.assert(_.isEmpty)
  }

  loggerFixture.test("IgnoreOnData only data") { implicit logger =>
    val policy = ErrorPolicy.IgnoreOnData.processor[Int]

    val result = policy.process[IO](rightResponse)

    result.assertEquals(1) *>
      logger.logged.assert(_.isEmpty)
  }

  loggerFixture.test("IgnoreOnData errors and data") { implicit logger =>
    val policy = ErrorPolicy.IgnoreOnData.processor[Int]

    val result = policy.process[IO](bothResponse)

    result.assertEquals(1) *>
      logger.logged.assertEquals(
        Vector(
          StructuredTestingLogger.WARN(
            "Received both data and errors",
            throwOpt = ResponseException(NonEmptyList.one(graphQlError), 1.some).some
          )
        )
      )
  }

  loggerFixture.test("RaiseAlways only errors") { implicit logger =>
    val policy = ErrorPolicy.RaiseAlways.processor[Int]

    val result = policy.process[IO](leftResponse)

    result.intercept[ResponseException[Int]] *>
      logger.logged.assert(_.isEmpty)
  }

  loggerFixture.test("RaiseAlways only data") { implicit logger =>
    val policy = ErrorPolicy.RaiseAlways.processor[Int]

    val result = policy.process[IO](rightResponse)

    result.assertEquals(1) *>
      logger.logged.assert(_.isEmpty)
  }

  loggerFixture.test("RaiseAlways errors and data") { implicit logger =>
    val policy = ErrorPolicy.RaiseAlways.processor[Int]

    val result = policy.process[IO](bothResponse)

    result.intercept[ResponseException[Int]] *> logger.logged.assert(_.isEmpty)
  }

  loggerFixture.test("ReturnAlways always returns whole response") { implicit logger =>
    val policy = ErrorPolicy.ReturnAlways.processor[Int]

    val result = policy.process[IO](bothResponse)

    result.assertEquals(bothResponse) *>
      logger.logged.assert(_.isEmpty)
  }

  loggerFixture.test("RaiseOnNoData only errors") { implicit logger =>
    val policy = ErrorPolicy.RaiseOnNoData.processor[Int]

    val result = policy.process[IO](leftResponse)

    result.intercept[ResponseException[Int]] *>
      logger.logged.assert(_.isEmpty)
  }

  loggerFixture.test("RaiseOnNoData only data") { implicit logger =>
    val policy = ErrorPolicy.RaiseOnNoData.processor[Int]

    val result = policy.process[IO](rightResponse)

    result.assertEquals(GraphQLDataResponse(1, None, None)) *>
      logger.logged.assert(_.isEmpty)
  }

  loggerFixture.test("RaiseOnNoData errors and data") { implicit logger =>
    val policy = ErrorPolicy.RaiseOnNoData.processor[Int]

    val result = policy.process[IO](bothResponse)

    result.assertEquals(GraphQLDataResponse(1, NonEmptyList.one(graphQlError).some, None)) *>
      logger.logged.assert(_.isEmpty)
  }

  val graphQlError = Arbitrary.arbString.arbitrary.map(GraphQLError(_)).sample.get

  def leftResponse  = GraphQLResponse.errors[Int](NonEmptyList.one(graphQlError))
  def rightResponse = GraphQLResponse[Int](result = Ior.right(1))
  def bothResponse  = GraphQLResponse[Int](result = Ior.both(NonEmptyList.one(graphQlError), 1))

}
