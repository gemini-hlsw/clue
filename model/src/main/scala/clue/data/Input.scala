// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.data

import cats.Align
import cats.Applicative
import cats.Eq
import cats.Eval
import cats.Functor
import cats.Monad
import cats.Show
import cats.Traverse
import cats.data.Ior
import cats.syntax.all._
import clue.data.syntax._
import io.circe._
import io.circe.syntax._

import scala.annotation.tailrec

sealed trait Input[+A] {
  def map[B](f: A => B): Input[B] =
    this match {
      case Ignore    => Ignore
      case Unassign  => Unassign
      case Assign(a) => Assign(f(a))
    }

  def fold[B](fundef: => B, funset: => B, fset: A => B): B =
    this match {
      case Ignore    => fundef
      case Unassign  => funset
      case Assign(a) => fset(a)
    }

  def flatten[B](implicit ev: A <:< Input[B]): Input[B] =
    this match {
      case Ignore    => Ignore
      case Unassign  => Unassign
      case Assign(a) => a
    }

  def flatMap[B](f: A => Input[B]): Input[B] =
    map(f).flatten

  def toOption: Option[A] =
    this match {
      case Assign(a) => a.some
      case _         => none
    }
}

case object Ignore                    extends Input[Nothing]
case object Unassign                  extends Input[Nothing]
final case class Assign[+A](value: A) extends Input[A]

object Input {
  def apply[A](a: A): Input[A] = Assign(a)

  /**
   * Alias for `apply`.
   */
  def assign[A](a: A): Input[A] = Input(a)

  def unassign[A]: Input[A] = Unassign

  def ignore[A]: Input[A] = Ignore

  def orIgnore[A](opt: Option[A]): Input[A] =
    opt match {
      case Some(a) => Assign(a)
      case None    => Ignore
    }

  def orUnassign[A](opt: Option[A]): Input[A] =
    opt match {
      case Some(a) => Assign(a)
      case None    => Unassign
    }

  implicit def inputEq[A: Eq]: Eq[Input[A]] =
    new Eq[Input[A]] {
      def eqv(x: Input[A], y: Input[A]): Boolean =
        x match {
          case Assign(ax) =>
            y match {
              case Assign(ay) => ax === ay
              case _          => false
            }
          case Ignore     =>
            y match {
              case Ignore => true
              case _      => false
            }
          case Unassign   =>
            y match {
              case Unassign => true
              case _        => false
            }
        }
    }

  implicit def inputShow[A: Show]: Show[Input[A]] =
    new Show[Input[A]] {
      override def show(t: Input[A]): String = t match {
        case Assign(a) => s"Set(${a.show})"
        case other @ _ => other.toString
      }
    }

  private val IgnoreValue: Json = Json.fromString("<<clue.data.Ignore>>")

  def dropIgnores(obj: JsonObject): JsonObject = obj.deepFilter((_, value) => value =!= IgnoreValue)

  implicit def inputEncoder[A: Encoder]: Encoder[Input[A]] = new Encoder[Input[A]] {
    override def apply(a: Input[A]): Json = a match {
      case Ignore    => IgnoreValue
      case Unassign  => Json.Null
      case Assign(a) => a.asJson
    }
  }

  implicit def inputDecoder[A: Decoder]: Decoder[Input[A]] = new Decoder[Input[A]] {
    override def apply(c: HCursor): Decoder.Result[Input[A]] =
      c.as[Option[A]].map(_.orUnassign)
  }

  implicit object InputCats extends Monad[Input] with Traverse[Input] with Align[Input] {

    override def pure[A](a: A): Input[A] = Input(a)

    @tailrec
    override def tailRecM[A, B](a: A)(f: A => Input[Either[A, B]]): Input[B] =
      f(a) match {
        case Ignore           => Ignore
        case Unassign         => Unassign
        case Assign(Left(a))  => tailRecM(a)(f)
        case Assign(Right(b)) => Assign(b)
      }

    override def flatMap[A, B](fa: Input[A])(f: A => Input[B]): Input[B] =
      fa.flatMap(f)

    override def traverse[F[_], A, B](
      fa: Input[A]
    )(f: A => F[B])(implicit F: Applicative[F]): F[Input[B]] =
      fa match {
        case Ignore    => F.pure(Ignore)
        case Unassign  => F.pure(Unassign)
        case Assign(a) => F.map(f(a))(Assign(_))
      }

    override def foldLeft[A, B](fa: Input[A], b: B)(f: (B, A) => B): B =
      fa match {
        case Ignore    => b
        case Unassign  => b
        case Assign(a) => f(b, a)
      }

    override def foldRight[A, B](fa: Input[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      fa match {
        case Ignore    => lb
        case Unassign  => lb
        case Assign(a) => f(a, lb)
      }

    override def functor: Functor[Input] = this

    override def align[A, B](fa: Input[A], fb: Input[B]): Input[Ior[A, B]] =
      alignWith(fa, fb)(identity)

    override def alignWith[A, B, P](fa: Input[A], fb: Input[B])(f: Ior[A, B] => C): Input[C] =
      fa match {
        case Ignore    =>
          fb match {
            case Ignore    => Ignore
            case Unassign  => Unassign
            case Assign(b) => Assign(f(Ior.right(b)))
          }
        case Unassign  =>
          fb match {
            case Ignore    => Ignore
            case Unassign  => Unassign
            case Assign(b) => Assign(f(Ior.right(b)))
          }
        case Assign(a) =>
          fb match {
            case Ignore    => Assign(f(Ior.left(a)))
            case Unassign  => Assign(f(Ior.left(a)))
            case Assign(b) => Assign(f(Ior.both(a, b)))
          }
      }
  }
}
