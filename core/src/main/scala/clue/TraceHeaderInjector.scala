// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

// Typeclass to abstract over injecting header for different transports, http, ws ...
trait TraceHeaderInjector[A]:
  def addHeaders(params: A, headers: Map[String, String]): A

object TraceHeaderInjector:
  def apply[A](using ev: TraceHeaderInjector[A]): TraceHeaderInjector[A] = ev

  given TraceHeaderInjector[Unit] with
    def addHeaders(params: Unit, headers: Map[String, String]): Unit = ()
