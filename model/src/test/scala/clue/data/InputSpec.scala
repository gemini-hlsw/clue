// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
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
import clue.data.syntax._
import io.circe._
import io.circe.testing.CodecTests
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import io.circe.testing.instances._
import io.circe.generic.semiauto._

case class SomeInput(value: Input[Int] = Ignore)
object SomeInput {
  implicit val someInputEncoder: Encoder[SomeInput] =
    deriveEncoder[SomeInput].mapJson(_.deepDropIgnore)

  implicit val someInputDecoder: Decoder[SomeInput] = Decoder.instance[SomeInput](
    _.downField("value").success
      .fold(SomeInput().asRight[DecodingFailure])(
        _.as[Option[Int]].map(_.fold(SomeInput(Unassign))(v => SomeInput(Assign(v))))
      )
  )

  implicit val someInputArb: Arbitrary[SomeInput] =
    Arbitrary(
      arbitrary[Input[Int]].map(SomeInput.apply)
    )

  implicit val someInputEq: Eq[SomeInput] = Eq.fromUniversalEquals
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

  property("Input[Int].toOption: Ignore is None") {
    Input.ignore[Int].toOption === none
  }

  property("Input[Int].toOption: Unassign is None") {
    Input.unassign[Int].toOption === none
  }

  property("Input[Int].toOption: Assign(a) is Some(a)") {
    forAll((i: Int) => Assign(i).toOption === i.some)
  }

  property("Input[Int] (Any.set): a.assign === Assign(a)") {
    forAll((i: Int) => i.assign === Assign(i))
  }

  property("Input[Int] (Option.orIgnore): None.orIgnore === Ignore") {
    none[Int].orIgnore match {
      case Ignore => true
      case _      => false
    }
  }

  property("Input[Int] (Option.orIgnore): Some(a).orIgnore === Assign(a)") {
    forAll((i: Int) => i.some.orIgnore === Assign(i))
  }

  property("Input[Int] (Option.orUnassign): None.orUnassign === Unassign") {
    none[Int].orUnassign match {
      case Unassign => true
      case _        => false
    }
  }

  property("Input[Int] (Option.orUnassign): Some(a).orUnassign === Assign(a)") {
    forAll((i: Int) => i.some.orUnassign === Assign(i))
  }

  property("Input[Input[Int]] (Input.flatten): Assign(Assign(a)).flatten === Assign(a)") {
    forAll((i: Int) => Assign(Assign(i)).flatten === Assign(i))
  }

  property("Input[Input[Int]] (Input.flatten): Assign(Ignore).flatten === Ignore") {
    Input[Input[Int]](Ignore).flatten === Ignore
  }

  checkAll("SomeInput", CodecTests[SomeInput].codec)
}
