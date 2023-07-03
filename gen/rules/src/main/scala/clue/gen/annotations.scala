// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import scalafix.v1._

import scala.meta._

// Adapted from simulacrum-scalafix
class AnnotationPattern(fqcn: String) {

  val name = fqcn.split('.').last

  val symbol = Symbol(s"$fqcn#")

  private def isAnnotationNamed(name: String)(typeTree: Type): Boolean = typeTree match {
    case Type.Select(_, Type.Name(`name`)) => true
    case Type.Name(`name`)                 => true
    case _                                 => false
  }

  def removeFrom(mods: List[Mod]): List[Mod] = mods.filter {
    case Mod.Annot(Init.After_4_6_0(typeTree, Name(""), _)) if isAnnotationNamed(name)(typeTree) =>
      false
    case _                                                                                       => true
  }

  def unapply(mods: List[Mod]): Option[List[Term]] = mods.reverse.collectFirst {
    case Mod.Annot(Init.After_4_6_0(typeTree, Name(""), args))
        if isAnnotationNamed(name)(typeTree) =>
      Some(args.toList.flatten)
  }.flatten
}

object GraphQLSchemaAnnotation extends AnnotationPattern("clue.annotation.GraphQLSchema")

object GraphQLAnnotation extends AnnotationPattern("clue.annotation.GraphQL")

object GraphQLStubAnnotation extends AnnotationPattern("clue.annotation.GraphQLStub")
