// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.sbt

import sbt._
import scalafix.sbt.ScalafixPlugin

import Keys._
import ScalafixPlugin.autoImport._

object CluePlugin extends AutoPlugin {
  override def buildSettings: Seq[Setting[_]] = Seq(
    scalafixDependencies += BuildInfo.organization %% BuildInfo.moduleName % BuildInfo.version,
  )
}
