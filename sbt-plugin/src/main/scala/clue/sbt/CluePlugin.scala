// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.sbt

import sbt._
import scalafix.sbt.ScalafixPlugin

import Keys._
import ScalafixPlugin.autoImport._

object CluePlugin extends AutoPlugin {

  object autoImport {
    lazy val clueSourceDirectory = settingKey[File]("Clue input schemas and sources")
  }
  import autoImport._

  override def buildSettings: Seq[Setting[_]] = Seq(
    scalafixScalaBinaryVersion                     := "2.13",
    scalafixDependencies += BuildInfo.organization %% BuildInfo.moduleName % BuildInfo.version
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    Compile / clueSourceDirectory := (Compile / sourceDirectory).value / "clue"
  )

  override def derivedProjects(proj: ProjectDefinition[_]): Seq[Project] = Seq(
    Project(
      proj.id + "-clue",
      new File(proj.base.getParent(), proj.base.getName() + "-clue")
    )
      .enablePlugins(ScalafixPlugin)
      .settings(
        Compile / clueSourceDirectory :=
          (LocalProject(proj.id) / Compile / clueSourceDirectory).value,
        scalaVersion                  := (LocalProject(proj.id) / scalaVersion).value,
        Compile / sourceDirectories += (Compile / clueSourceDirectory).value / "scala",
        Compile / resourceDirectories += (Compile / clueSourceDirectory).value / "resources",
        Compile / dependencyClasspath :=
          (LocalProject(proj.id) / Compile / dependencyClasspath).value,

        // register generator
        LocalProject(proj.id) / Compile / sourceGenerators += Def.taskDyn {
          val root    = (LocalRootProject / baseDirectory).value.toPath
          val from    = (Compile / clueSourceDirectory).value
          val to      = (LocalProject(proj.id) / Compile / sourceManaged).value
          val outFrom = root.relativize(from.toPath).normalize
          val outTo   = root.relativize(to.toPath).normalize
          Def.task {
            streams.value.log.info(s"Generating Clue code from $outFrom to $outTo")
            val _ = (Compile / scalafix)
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
