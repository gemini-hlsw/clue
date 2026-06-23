// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.sbt

import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*
import sbt.*
import sbtcrossproject.CrossPlugin
import scalafix.sbt.ScalafixPlugin

import Keys.*
import CrossPlugin.autoImport.*
import ScalafixPlugin.autoImport.*

object CluePlugin extends AutoPlugin {

  // Pulls in ScalafixPlugin so this project gets the `scalafix` task (used by `clueCheck`).
  override def requires: Plugins = ScalafixPlugin

  object autoImport {
    lazy val clueSourceDirectory   = settingKey[File]("Clue input schemas and sources")
    lazy val clueSourceGenerators  = settingKey[Seq[Task[Seq[File]]]]("Clue source generators")
    lazy val clueClean             = taskKey[Unit]("Clue clean task")
    lazy val clueCheck             =
      taskKey[Unit](
        "Validate hand-written clue operations/subqueries in this project's own sources " +
          "(those not produced by the generator) against the schema."
      )
    lazy val clueValidateOnCompile =
      settingKey[Boolean](
        "Whether to validate hand-written clue operations/subqueries on every compile (default true)."
      )
  }
  import autoImport._

  override def buildSettings: Seq[Setting[_]] = Seq(
    scalafixDependencies += BuildInfo.organization %% BuildInfo.rulesModule % BuildInfo.version,
    Compile / clueSourceGenerators                 := Seq.empty,
    clueClean                                      := {}
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    Compile / clueSourceDirectory :=
      crossProjectCrossType.?.value
        .flatMap { crossType =>
          crossType.sharedSrcDir(baseDirectory.value, "clue").map(_.getParentFile)
        }
        .getOrElse(sourceDirectory.value / "clue"),
    Compile / sourceGenerators ++= (Compile / clueSourceGenerators).value, // workaround for sbt/sbt#7173
    libraryDependencies += BuildInfo.organization %%% BuildInfo.coreModule % BuildInfo.version,
    // another workaround
    clean                                          := clean.dependsOn(clueClean).value,

    // Validation of hand-written operations/subqueries living in this project's own sources. The
    // generator only scans `clueSourceDirectory`; the validation rule covers the rest.
    // semanticdb is required by the rule.
    semanticdbEnabled     := true,
    semanticdbVersion     := scalafixSemanticdb.revision,
    clueCheck             := (Compile / scalafix).toTask(" GraphQLValidate --check").value,
    clueValidateOnCompile := true
  ) ++
    // Run validation on every compile. We decorate `compile` to run the real compilation first
    // (producing fresh semanticdb) and then the validation rule over it — the same ordering as
    // scalafix's own on-compile hook, but invoking `GraphQLValidate` explicitly so no extra
    // `.scalafix.conf` `triggered` section (and thus no generated config file) is needed.
    //
    // The `--triggered` flag is essential: without it, the `scalafix` task `dependsOn(compile)`
    // (to refresh semanticdb), which here would mean `compile` depends on `scalafix` depends on
    // `compile` — a self-dependency that deadlocks in larger build graphs. `--triggered` drops that
    // compile dependency (we've already compiled), breaking the cycle, while the explicitly named
    // rule still runs.
    Seq(Compile, Test).map { config =>
      config / compile := Def.taskDyn {
        val analysis = (config / compile).value
        if (clueValidateOnCompile.value)
          Def.task {
            val _ = (config / scalafix).toTask(" GraphQLValidate --check --triggered").value
            analysis
          }
        else Def.task(analysis)
      }.value
    }

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
        Compile / unmanagedSourceDirectories += (Compile / clueSourceDirectory).value / "scala",
        Compile / dependencyClasspath :=
          (LocalProject(proj.id) / Compile / dependencyClasspath).value,

        // register generator
        LocalProject(proj.id) / Compile / clueSourceGenerators += Def.taskDyn {
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

        // register clean
        LocalProject(proj.id) / clueClean :=
          clean.value,

        // scalafix stuff
        semanticdbEnabled := true,
        semanticdbVersion := scalafixSemanticdb.revision,

        // no publish
        publish         := {},
        publishLocal    := {},
        publishArtifact := false,
        publish / skip  := true
      )
  )
}
