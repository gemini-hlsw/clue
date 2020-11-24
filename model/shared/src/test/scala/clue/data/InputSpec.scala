// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.data

import cats.Eq
import munit.DisciplineSuite
import arb.ArbInput._
import cats.syntax.all._
import cats.kernel.laws.discipline.EqTests
import org.scalacheck.Prop.forAll
import cats.laws.discipline._
import cats.laws.discipline.arbitrary._
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import clue.data.implicits._
import io.circe._
import io.circe.testing.CodecTests
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import io.circe.testing.instances._
import io.circe.generic.extras.semiauto._
import io.circe.generic.extras.Configuration

case class SomeInput(value: Input[Int] = Undefined)
object SomeInput {
  implicit val customConfig: Configuration = Configuration.default.withDefaults

  implicit val someInputDecoder: Decoder[SomeInput] =
    deriveConfiguredDecoder[SomeInput]
  implicit val someInputEncoder: Encoder[SomeInput] =
    deriveConfiguredEncoder[SomeInput].mapJson(_.deepDropUndefined)
  implicit val someInputArb: Arbitrary[SomeInput]   =
    Arbitrary(
      arbitrary[Input[Int]].map(SomeInput.apply)
    )
  implicit val someInputEq: Eq[SomeInput]           = Eq.fromUniversalEquals
}

class InputSpec extends DisciplineSuite {
  implicit def iso: Isomorphisms[Input] = Isomorphisms.invariant[Input]

  checkAll(
    "Input[Int].EqLaws",
    EqTests[Input[Int]].eqv
  )

  checkAll(
    "Input[Int].AlignLaws",
    AlignTests[Input].align[Int, Int, Int, Int]
  )

  checkAll(
    "Input[Int].MonadLaws",
    MonadTests[Input].monad[Int, Int, String]
  )

  checkAll(
    "Input[Int].TraverseLaws",
    TraverseTests[Input].traverse[Int, Int, Int, Int, Option, Option]
  )

  property("Input[Int].toOption: Undefined is None") {
    Input.undefined[Int].toOption === none
  }

  property("Input[Int].toOption: Unset is None") {
    Input.unset[Int].toOption === none
  }

  property("Input[Int].toOption: Set(a) is Some(a)") {
    forAll((i: Int) => Set(i).toOption === i.some)
  }

  property("Input[Int] (Any.set): a.set === Set(a)") {
    forAll((i: Int) => i.set === Set(i))
  }

  property("Input[Int] (Option.toInput): None.orUndefined === Undefined") {
    none[Int].orUndefined match {
      case Undefined => true
      case _         => false
    }
  }

  property("Input[Int] (Option.toInput): Some(a).orUndefined === Set(a)") {
    forAll((i: Int) => i.some.orUndefined === Set(i))
  }

  property("Input[Int] (Option.toInput): None.orUnset === Undefined") {
    none[Int].orUnset match {
      case Unset => true
      case _     => false
    }
  }

  property("Input[Int] (Option.toInput): Some(a).orUnset === Set(a)") {
    forAll((i: Int) => i.some.orUnset === Set(i))
  }

  property("Input[Input[Int]] (Input.flatten): Set(Set(a)).flatten === Set(a)") {
    forAll((i: Int) => Set(Set(i)).flatten === Set(i))
  }

  property("Input[Input[Int]] (Input.flatten): Set(Undefined).flatten === Undefined") {
    Input[Input[Int]](Undefined).flatten === Undefined
  }

  checkAll("SomeInput", CodecTests[SomeInput].codec)
}
