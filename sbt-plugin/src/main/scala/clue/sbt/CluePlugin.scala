// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.sbt

import sbt.*
import sbtcompat.PluginCompat.*
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

  override def buildSettings: Seq[Setting[?]] = Seq(
    scalafixDependencies += BuildInfo.organization %% BuildInfo.rulesModule % BuildInfo.version,
    Compile / clueSourceGenerators                 := Seq.empty,
    clueClean                                      := {}
  )

  override def projectSettings: Seq[Setting[?]] = Seq(
    Compile / clueSourceDirectory :=
      crossProjectCrossType.?.value
        .flatMap { crossType =>
          crossType.sharedSrcDir(baseDirectory.value, "clue").map(_.getParentFile)
        }
        .getOrElse(sourceDirectory.value / "clue"),
    Compile / sourceGenerators ++= (Compile / clueSourceGenerators).value, // workaround for sbt/sbt#7173
    libraryDependencies += PluginCompat
      .platformModuleID(BuildInfo.organization, BuildInfo.coreModule, BuildInfo.version)
      .value,
    // another workaround
    clean := clean.dependsOn(clueClean).value,

    // Validation of hand-written operations/subqueries living in this project's own sources. The
    // generator only scans `clueSourceDirectory`; the validation rule covers the rest.
    // semanticdb is required by the rule.
    semanticdbEnabled     := true,
    semanticdbVersion     := scalafixSemanticdb.revision,
    clueCheck             := Def.uncached((Compile / scalafix).toTask(" GraphQLValidate --check").value),
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
      config / compile := Def.uncached(Def.taskDyn {
        val analysis = (config / compile).value
        if (clueValidateOnCompile.value)
          Def.task {
            val _ = (config / scalafix).toTask(" GraphQLValidate --check --triggered").value
            analysis
          }
        else Def.task(analysis)
      }.value)
    }

  // What the last successful run knew about a source and its target.
  private case class Seen(sourceHash: String, targetModifiedMillis: Long, copied: Boolean)

  private def readSeen(record: File): Map[String, Seen] =
    if (record.isFile)
      IO.readLines(record)
        .map(_.split("\t", 4))
        .collect { case Array(sourceHash, targetModifiedMillis, copied, path) =>
          path -> Seen(sourceHash, targetModifiedMillis.toLong, copied.toBoolean)
        }
        .toMap
    else Map.empty

  private def writeSeen(record: File, seen: Map[String, Seen]): Unit =
    IO.writeLines(
      record,
      seen.toList.sortBy(_._1).map { case (path, s) =>
        List(s.sourceHash, s.targetModifiedMillis.toString, s.copied.toString, path).mkString("\t")
      }
    )

  // scalafix only writes the sources the generator changed, so the others would never be compiled.
  // They are copied as they are, which lets `clueSourceDirectory` also hold plain definitions (such
  // as hand-written subqueries, or the classes the operations decode to).
  //
  // Whether the generator changed a source is deduced from what scalafix did, never from the
  // source's text: scalafix runs again on every source whose contents changed, and always writes
  // the ones it rewrites. So a changed source whose target was not written is one it left alone.
  private def syncUnprocessedSources(from: File, to: File, record: File): Unit = {
    val previous: Map[String, Seen] = readSeen(record)
    val sources: Map[String, File]  =
      (from ** "*.scala")
        .get()
        .flatMap(source => IO.relativize(from, source).map(_ -> source))
        .toMap

    // The targets of removed sources, unless something else wrote them since.
    (previous -- sources.keys).foreach { case (path, seen) =>
      val target: File = new File(to, path)
      if (target.lastModified == seen.targetModifiedMillis) IO.delete(target)
    }

    val current: Map[String, Seen] = sources.map { case (path, source) =>
      val target: File       = new File(to, path)
      val sourceHash: String = Hash.toHex(Hash(source))
      val last: Option[Seen] = previous.get(path)
      val changed: Boolean   = !last.exists(_.sourceHash == sourceHash)
      val written: Boolean   =
        target.exists && !last.exists(_.targetModifiedMillis == target.lastModified)
      // An unchanged source was skipped by scalafix, so it is what it was the last time.
      val copy: Boolean      =
        if (changed) !written
        else last.exists(_.copied) && !target.exists
      if (copy) IO.copyFile(source, target)
      path -> Seen(sourceHash, target.lastModified, copy || (!changed && last.exists(_.copied)))
    }

    writeSeen(record, current)
  }

  override def derivedProjects(proj: ProjectDefinition[?]): Seq[Project] = Seq(
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
        Compile / dependencyClasspath := Def.uncached(
          (LocalProject(proj.id) / Compile / dependencyClasspath).value
        ),

        // register generator
        LocalProject(proj.id) / Compile / clueSourceGenerators += Def.taskDyn {
          val root    = (LocalRootProject / baseDirectory).value.toPath
          val from    = (Compile / clueSourceDirectory).value
          val to      = (LocalProject(proj.id) / Compile / sourceManaged).value
          val outFrom = root.relativize(from.toPath).normalize
          val outTo   = root.relativize(to.toPath).normalize
          Def.task {
            streams.value.log.info(s"Generating Clue code from $outFrom to $outTo")
            // A failed generation fails the task here, so nothing is copied then.
            val _ = (Compile / scalafix)
              .toTask(s" GraphQLGen --out-from=$outFrom --out-to=$outTo")
              .value
            syncUnprocessedSources(from, to, streams.value.cacheDirectory / "clue-sources")
            (to ** "*.scala").get()
          }
        }.taskValue,

        // register clean
        LocalProject(proj.id) / clueClean := Def.uncached(clean.value),

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
