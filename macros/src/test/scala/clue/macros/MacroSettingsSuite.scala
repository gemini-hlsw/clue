package clue.macros

import munit._
import java.io.File
import cats.kernel.laws.discipline.MonoidTests
import org.scalacheck.Gen
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._

class MacroSettingsSuite extends DisciplineSuite {

  val genMacroSettings: Gen[MacroSettings] =
    for {
      schemaDirs              <- Gen.listOfN(5, arbitrary[String]).map(_.map(f => new File(f)))
      catsEq                  <- arbitrary[Boolean]
      catsShow                <- arbitrary[Boolean]
      monocleLenses           <- arbitrary[Boolean]
      scalajsReactReusability <- arbitrary[Boolean]
    } yield MacroSettings(schemaDirs, catsEq, catsShow, monocleLenses, scalajsReactReusability)

  implicit val arbitraryMacroSettings: Arbitrary[MacroSettings] = Arbitrary(genMacroSettings)

  checkAll("Monoid[MacroSettings]", MonoidTests[MacroSettings].monoid)

  test("Parse") {
    val settings =
      List(
        "schemaDir=/main/resources/graphql/schemas/",
        "schemaDir=/test/resources/graphql/schemas/",
        "cats.eq=true",
        "cats.show=true",
        "monocle.lenses=true",
        "scalajs-react.reusability=true"
      ).map(s => s"${MacroSettings.OptionPrefix}.$s")

    println(settings)

    assertEquals(
      MacroSettings.fromCtxSettings(settings),
      MacroSettings(
        List(new File("/main/resources/graphql/schemas/"),
             new File("/test/resources/graphql/schemas/")
        ),
        true,
        true,
        true,
        true
      )
    )
  }
}
