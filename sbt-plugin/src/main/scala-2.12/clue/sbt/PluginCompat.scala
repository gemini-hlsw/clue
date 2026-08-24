// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.sbt

import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._

/**
 * sbt 1.x half of the compatibility shim. See `src/main/scala-3` for the sbt 2.x half.
 */
private[sbt] object PluginCompat {

  /**
   * A dependency on a module that is cross-published for every platform (JVM, Scala.js, Scala
   * Native). On sbt 1.x the platform suffix comes from the `%%%` operator of sbt-platform-deps.
   */
  def platformModuleID(
    organization: String,
    name:         String,
    revision:     String
  ): Def.Initialize[ModuleID] =
    Def.setting(organization %%% name % revision)
}
