package test

object App {
  def main(args: Array[String]): Unit =
    // compile-check that the edit of the plain source was copied
    StarWarsQuery.Variables(CharacterId("r2d2").quoted)
}
