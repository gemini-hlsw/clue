package clue.macros

object Schemas {
  type Explore
  @GraphQLSchema
  object Explore {
    // Scalars
    type Cloudcover       = String
    type Imagequality     = String
    type Skybackground    = String
    type Watervapor       = String
    type Obsstatus        = String
    type Targetobjecttype = String
    // Enums
    // type Order_by               = String
    // type Constraints_constraint = String
    // Mappings
    type Uuid             = java.util.UUID
  }

  type StarWars
}
