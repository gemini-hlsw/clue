// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.data

// import io.circe.Json

package object syntax {
  implicit final class AnyToInputOps[A](private val a: A) extends AnyVal {
    def assign: Input[A] = Assign(a)
  }

  implicit final class AnyOptionToInputOps[A](private val a: Option[A]) extends AnyVal {
    def orIgnore: Input[A]   = Input.orIgnore(a)
    def orUnassign: Input[A] = Input.orUnassign(a)
  }

  // implicit final class JsonOps(private val json: Json) extends AnyVal {
  //   def deepDropIgnore: Json = json.foldWith(Input.dropIgnoreFolder)
  // }
}
