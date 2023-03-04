// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import io.circe.Json
import io.circe.JsonObject

package object data {
  implicit class JsonObjectOps(val value: JsonObject) extends AnyVal {
    def deepFilter(p: (String, Json) => Boolean): JsonObject =
      value.toIterable.foldLeft(JsonObject.empty) { case (acc, (key, value)) =>
        if (p(key, value))
          acc.add(key, value.asObject.fold(value)(obj => Json.fromJsonObject(obj.deepFilter(p))))
        else acc
      }
  }
}
