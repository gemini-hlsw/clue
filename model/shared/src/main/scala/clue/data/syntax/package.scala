// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.data

import io.circe.Json

package object syntax {
  implicit final class AnyToInputOps[A](private val a: A) extends AnyVal {
    def set: Input[A] = Set(a)
  }

  implicit final class AnyOptionToInputOps[A](private val a: Option[A]) extends AnyVal {
    def orUndefined: Input[A] = Input.orUndefined(a)
    def orUnset: Input[A]     = Input.orUnset(a)
  }

  implicit final class JsonOps(private val json: Json) extends AnyVal {
    def deepDropUndefined: Json = json.foldWith(Input.dropUndefinedFolder)
  }
}
