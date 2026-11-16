package test

object App {
  def main(args: Array[String]): Unit =
    // compile-check that the query was generated and the plain source copied
    StarWarsQuery.Variables(CharacterId("r2d2").value)
}
