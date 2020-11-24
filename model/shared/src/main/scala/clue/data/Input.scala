// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.data

import cats.syntax.all._
import cats.Eq
import cats.Align
import cats.Applicative
import cats.Eval
import cats.Functor
import cats.Traverse
import cats.data.Ior
import scala.annotation.tailrec
import cats.Monad
import cats.Show
import io.circe._
import io.circe.syntax._
import clue.data.syntax._

sealed trait Input[+A] {
  def map[B](f: A => B): Input[B] =
    this match {
      case Undefined => Undefined
      case Unset     => Unset
      case Set(a)    => Set(f(a))
    }

  def fold[B](fundef: => B, funset: => B, fset: A => B): B =
    this match {
      case Undefined => fundef
      case Unset     => funset
      case Set(a)    => fset(a)
    }

  def flatten[B](implicit ev: A <:< Input[B]): Input[B] =
    this match {
      case Undefined => Undefined
      case Unset     => Unset
      case Set(a)    => a
    }

  def flatMap[B](f: A => Input[B]): Input[B] =
    map(f).flatten

  def toOption: Option[A] =
    this match {
      case Set(a) => a.some
      case _      => none
    }
}

final case object Undefined extends Input[Nothing]
final case object Unset     extends Input[Nothing]
final case class Set[A](value: A) extends Input[A]

object Input {
  def apply[A](a: A): Input[A] = Set(a)

  def unset[A]: Input[A] = Unset

  def undefined[A]: Input[A] = Undefined

  def orUndefined[A](opt: Option[A]): Input[A] =
    opt match {
      case Some(a) => Set(a)
      case None    => Undefined
    }

  def orUnset[A](opt: Option[A]): Input[A] =
    opt match {
      case Some(a) => Set(a)
      case None    => Unset
    }

  implicit def inputEq[A: Eq]: Eq[Input[A]] =
    new Eq[Input[A]] {
      def eqv(x: Input[A], y: Input[A]): Boolean =
        x match {
          case Undefined =>
            y match {
              case Undefined => true
              case _         => false
            }
          case Unset     =>
            y match {
              case Unset => true
              case _     => false
            }
          case Set(ax)   =>
            y match {
              case Set(ay) => ax === ay
              case _       => false
            }
        }
    }

  implicit def inputShow[A: Show]: Show[Input[A]] =
    new Show[Input[A]] {
      override def show(t: Input[A]): String = t match {
        case Set(a)    => s"Set(${a.show})"
        case other @ _ => other.toString
      }
    }

  private val UndefinedValue: Json = Json.fromString("clue.data.Undefined")

  val dropUndefinedFolder: Json.Folder[Json] = new Json.Folder[Json] {
    def onNull: Json = Json.Null
    def onBoolean(value: Boolean): Json      = Json.fromBoolean(value)
    def onNumber(value:  JsonNumber): Json   = Json.fromJsonNumber(value)
    def onString(value:  String): Json       = Json.fromString(value)
    def onArray(value:   Vector[Json]): Json =
      Json.fromValues(value.collect {
        case v if v =!= UndefinedValue => v.foldWith(this)
      })
    def onObject(value:  JsonObject): Json   =
      Json.fromJsonObject(
        value.filter { case (_, v) => v =!= UndefinedValue }.mapValues(_.foldWith(this))
      )
  }

  implicit def inputEncoder[A: Encoder]: Encoder[Input[A]] = new Encoder[Input[A]] {
    override def apply(a: Input[A]): Json = a match {
      case Undefined => UndefinedValue
      case Unset     => Json.Null
      case Set(a)    => a.asJson
    }
  }

  implicit def inputDecoder[A: Decoder]: Decoder[Input[A]] = new Decoder[Input[A]] {
    override def apply(c: HCursor): Decoder.Result[Input[A]] =
      c.as[Option[A]].map(_.orUnset)
  }

  implicit object InputCats extends Monad[Input] with Traverse[Input] with Align[Input] {

    override def pure[A](a: A): Input[A] = Input(a)

    @tailrec
    override def tailRecM[A, B](a: A)(f: A => Input[Either[A, B]]): Input[B] =
      f(a) match {
        case Undefined     => Undefined
        case Unset         => Unset
        case Set(Left(a))  => tailRecM(a)(f)
        case Set(Right(b)) => Set(b)
      }

    override def flatMap[A, B](fa: Input[A])(f: A => Input[B]): Input[B] =
      fa.flatMap(f)

    override def traverse[F[_], A, B](
      fa: Input[A]
    )(f:  A => F[B])(implicit F: Applicative[F]): F[Input[B]] =
      fa match {
        case Undefined => F.pure(Undefined)
        case Unset     => F.pure(Unset)
        case Set(a)    => F.map(f(a))(Set(_))
      }

    override def foldLeft[A, B](fa: Input[A], b: B)(f: (B, A) => B): B =
      fa match {
        case Undefined => b
        case Unset     => b
        case Set(a)    => f(b, a)
      }

    override def foldRight[A, B](fa: Input[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      fa match {
        case Undefined => lb
        case Unset     => lb
        case Set(a)    => f(a, lb)
      }

    override def functor: Functor[Input] = this

    override def align[A, B](fa: Input[A], fb: Input[B]): Input[Ior[A, B]] =
      alignWith(fa, fb)(identity)

    override def alignWith[A, B, C](fa: Input[A], fb: Input[B])(f: Ior[A, B] => C): Input[C] =
      fa match {
        case Undefined =>
          fb match {
            case Undefined => Undefined
            case Unset     => Unset
            case Set(b)    => Set(f(Ior.right(b)))
          }
        case Unset     =>
          fb match {
            case Undefined => Undefined
            case Unset     => Unset
            case Set(b)    => Set(f(Ior.right(b)))
          }
        case Set(a)    =>
          fb match {
            case Undefined => Set(f(Ior.left(a)))
            case Unset     => Set(f(Ior.left(a)))
            case Set(b)    => Set(f(Ior.both(a, b)))
          }
      }
  }
}
