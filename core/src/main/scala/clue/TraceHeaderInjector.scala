// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

trait TraceHeaderInjector[P]:
  def addHeaders(params: P, headers: Map[String, String]): P

object TraceHeaderInjector:
  def apply[P](using ev: TraceHeaderInjector[P]): TraceHeaderInjector[P] = ev

  given TraceHeaderInjector[Unit] with
    def addHeaders(params: Unit, headers: Map[String, String]): Unit = ()
