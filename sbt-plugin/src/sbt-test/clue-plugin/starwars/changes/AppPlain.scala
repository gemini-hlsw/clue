package test

object App {
  def main(args: Array[String]): Unit =
    // compile-check that the plain source replaced what had been generated from it
    StarWarsQuery.Variables(CharacterId(HeroQuery.Plain).quoted)
}
