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

  override def requires = ScalafixPlugin

  override def buildSettings: Seq[Setting[_]] = Seq(
    scalafixDependencies += BuildInfo.organization %% BuildInfo.moduleName % BuildInfo.version
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    Compile / sourceGenerators += Def.taskDyn {
      val root    = (LocalRootProject / baseDirectory).value.toURI.toString
      val graphql = LocalProject(thisProject.value.id + "-clue")
      val from    = (graphql / Compile / sourceDirectory).value
      val to      = (Compile / sourceManaged).value
      val outFrom = from.toURI.toString.stripSuffix("/").stripPrefix(root)
      val outTo   = to.toURI.toString.stripSuffix("/").stripPrefix(root)
      Def.task {
        (graphql / Compile / scalafix)
          .toTask(s" GraphQLGen --out-from=$outFrom --out-to=$outTo")
          .value
        (to ** "*.scala").get
      }
    }.taskValue
  )

  override def derivedProjects(proj: ProjectDefinition[_]): Seq[Project] = Seq(
    Project(
      proj.id + "-clue",
      new File(proj.base.getParent(), proj.base.getName() + "-clue")
    ).settings(
      Compile / sourceDirectories += clueSourceDirectory.value,
      // no publish
      publish         := {},
      publishLocal    := {},
      publishArtifact := false,
      publish / skip  := true
    ).settings(proj.settings)
      .enablePlugins(proj.plugins)
  )
}
