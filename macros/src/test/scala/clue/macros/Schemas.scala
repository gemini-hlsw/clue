package clue.macros

object Schemas {
  @GraphQLSchema(debug = false)
  object Explore {
    object Scalars {
      type Cloudcover       = String
      type Imagequality     = String
      type Skybackground    = String
      type Watervapor       = String
      type Obsstatus        = String
      type Targetobjecttype = String
    }

    object Types {}
  }

  type StarWars
}
