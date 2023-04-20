package test

object App {
  def main(args: Array[String]): Unit =
    StarWarsQuery.Variables("r2d2") // compile-check that this was generated
}
