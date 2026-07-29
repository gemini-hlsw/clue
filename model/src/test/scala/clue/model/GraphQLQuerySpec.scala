// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.syntax.option.*
import munit.FunSuite

final class GraphQLQuerySpec extends FunSuite:

  // A document as it appears in generated code: triple-quoted, with leading
  // newline and indentation before the operation keyword.
  private def check(document: String, expectedSummary: String): Unit =
    assertEquals(GraphQLQuery(document).querySummary, expectedSummary)

  test("querySummary uses the named operation when present") {
    check(
      """
        query Program {
          program(programId: "p-2") { id }
        }
      """,
      "query-Program"
    )
  }

  test("querySummary uses the named operation even with variable definitions") {
    check(
      """
        query ObservationVisits($obsId: ObservationId!) {
          observation(observationId: $obsId) { id }
        }
      """,
      "query-ObservationVisits"
    )
  }

  test("querySummary falls back to the first root field for an anonymous query with vars") {
    check(
      """
        query ($charId: ID!) {
          character(id: $charId) { id }
        }
      """,
      "query-character"
    )
  }

  test("querySummary falls back to the first root field for an anonymous query without vars") {
    check("query { character { id } }", "query-character")
  }

  test("querySummary handles a named mutation") {
    check("mutation AddFoo($x: ID!) { addFoo(id: $x) { id } }", "mutation-AddFoo")
  }

  test("querySummary falls back to the first root field for an anonymous mutation") {
    check("mutation { addFoo { id } }", "mutation-addFoo")
  }

  test("querySummary handles a named subscription") {
    check("subscription Sub { x }", "subscription-Sub")
  }

  test("querySummary is robust to a name immediately followed by the selection set") {
    check("query Program{ program { id } }", "query-Program")
  }

  test("querySummary skips leading comments") {
    check(
      """
        # Everything we need about a program.
        query Program {
          program(programId: "p-2") { id }
        }
      """,
      "query-Program"
    )
  }

  test("querySummary skips leading fragment definitions") {
    check(
      """
        fragment programFields on Program {
          id
          name
        }

        query Program {
          program(programId: "p-2") { ...programFields }
        }
      """,
      "query-Program"
    )
  }

  test("querySummary picks the operation's root field, not a leading fragment's") {
    check(
      """
        fragment programFields on Program {
          id
        }

        query {
          program(programId: "p-2") { ...programFields }
        }
      """,
      "query-program"
    )
  }

  test("querySummary handles a directive between the name and the selection set") {
    check("query Program @cached { program { id } }", "query-Program")
  }

  test("querySummary treats a bare selection set as an anonymous query") {
    check("{ character { id } }", "query-character")
  }

  test("querySummary reports both parts as unknown when nothing parses") {
    check("not a graphql document", "<queryType?>-<queryName?>")
  }

  test("operationType reports the keyword") {
    assertEquals(GraphQLQuery("query Program { program }").operationType, "query".some)
    assertEquals(GraphQLQuery("mutation AddFoo { addFoo }").operationType, "mutation".some)
    assertEquals(GraphQLQuery("subscription Sub { x }").operationType, "subscription".some)
  }

  test("operationType defaults a bare selection set to query") {
    assertEquals(GraphQLQuery("{ character { id } }").operationType, "query".some)
  }

  test("operationType is empty when no operation can be found") {
    assertEquals(GraphQLQuery("not a graphql document").operationType, none)
  }
end GraphQLQuerySpec
