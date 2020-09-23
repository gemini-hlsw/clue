package clue.macros

// Parameter order and names must match exactly between this class and annotation class.
case class GraphQLOptionalParams(
  val mappings: Some[Map[String, String]] = Some(Map.empty),
  val eq:       Option[Boolean] = None,
  val show:     Option[Boolean] = None,
  val lenses:   Option[Boolean] = None,
  val reuse:    Option[Boolean] = None,
  val debug:    Some[Boolean] = Some(false)
) {
  def resolve(settings: MacroSettings): GraphQLParams =
    GraphQLParams(
      GraphQLParams.DefaultMappings ++ mappings.get,
      eq.getOrElse(settings.catsEq),
      show.getOrElse(settings.catsShow),
      lenses.getOrElse(settings.monocleLenses),
      reuse.getOrElse(settings.scalajsReactReusability),
      debug.get
    )
}

protected[macros] case class GraphQLParams(
  mappings: Map[String, String],
  eq:       Boolean,
  show:     Boolean,
  lenses:   Boolean,
  reuse:    Boolean,
  debug:    Boolean
)
object GraphQLParams {
  val DefaultMappings: Map[String, String] = Map("ID" -> "String", "uuid" -> "java.util.UUID")
}
