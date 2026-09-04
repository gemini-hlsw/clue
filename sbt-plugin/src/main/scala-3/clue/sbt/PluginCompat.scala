// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.sbt

import sbt.*

/**
 * sbt 2.x half of the compatibility shim. See `src/main/scala-2.12` for the sbt 1.x half.
 */
private[sbt] object PluginCompat {

  /**
   * A dependency on a module that is cross-published for every platform (JVM, Scala.js, Scala
   * Native). On sbt 2.x the `%%` operator is platform-aware, so it replaces `%%%`.
   */
  def platformModuleID(
    organization: String,
    name:         String,
    revision:     String
  ): Def.Initialize[ModuleID] =
    Def.setting(organization %% name % revision)
}
