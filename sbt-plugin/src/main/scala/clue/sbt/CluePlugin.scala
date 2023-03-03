// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.sbt

import sbt._
import scalafix.sbt.ScalafixPlugin

import Keys._
import ScalafixPlugin.autoImport._

object CluePlugin extends AutoPlugin {

  object autoImport {
    lazy val clueSourceDirectory = settingKey[File]("Clue input sources")
  }
  import autoImport._

  override def buildSettings: Seq[Setting[_]] = Seq(
    scalafixDependencies += BuildInfo.organization %% BuildInfo.moduleName % BuildInfo.version
  )

  override def derivedProjects(proj: ProjectDefinition[_]): Seq[Project] = Seq(
    Project(
      proj.id + "-clue",
      new File(proj.base.getParent(), proj.base.getName() + "-clue")
    )
      .settings(proj.settings)
      .enablePlugins(proj.plugins, ScalafixPlugin)
      .settings(
        Compile / sourceDirectories := Seq(clueSourceDirectory.value),

        // register generator
        LocalProject(proj.id) / Compile / sourceGenerators += Def.taskDyn {
          val root    = (LocalRootProject / baseDirectory).value.toPath
          val from    = (Compile / sourceDirectory).value
          val to      = (LocalProject(proj.id) / Compile / sourceManaged).value
          val outFrom = root.relativize(from.toPath).normalize
          val outTo   = root.relativize(to.toPath).normalize
          Def.task {
            (Compile / scalafix)
              .toTask(s" GraphQLGen --out-from=$outFrom --out-to=$outTo")
              .value
            (to ** "*.scala").get
          }
        }.taskValue,

        // no publish
        publish         := {},
        publishLocal    := {},
        publishArtifact := false,
        publish / skip  := true
      )
  )
}
