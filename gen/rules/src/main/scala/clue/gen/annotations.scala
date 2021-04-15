// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import scala.meta._
import scala.reflect.ClassTag
import clue.annotation._
import scalafix.v1._
import scala.annotation.StaticAnnotation

// Adapted from simulacrum-scalafix
class AnnotationPattern[T <: StaticAnnotation](implicit classTag: ClassTag[T]) {

  private val clazz = classTag.runtimeClass

  val name = clazz.getSimpleName

  val symbol = Symbol(s"${clazz.getName}#")

  private def isAnnotationNamed(name: String)(typeTree: Type): Boolean = typeTree match {
    case Type.Select(_, Type.Name(`name`)) => true
    case Type.Name(`name`)                 => true
    case _                                 => false
  }

  def removeFrom(mods: List[Mod]): List[Mod] = mods.filter {
    case Mod.Annot(Init(typeTree, Name(""), _)) if isAnnotationNamed(name)(typeTree) => false
    case _                                                                           => true
  }

  def unapply(mods: List[Mod]): Option[List[Term]] = mods.reverse.collectFirst {
    case Mod.Annot(Init(typeTree, Name(""), args)) if isAnnotationNamed(name)(typeTree) =>
      Some(args.flatten)
  }.flatten
}

object GraphQLSchemaAnnotation extends AnnotationPattern[GraphQLSchema]

object GraphQLAnnotation extends AnnotationPattern[GraphQL]
