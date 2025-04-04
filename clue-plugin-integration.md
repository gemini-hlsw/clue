# How to setup the Clue Plugin

1. **Plugin Configuration**:
   - Add the Clue plugin to the project in `project/plugins.sbt`:
     ```scala
     addSbtPlugin("edu.gemini" % "sbt-clue" % <version>)
     ```
   - Add CluePlugin to the project in `build.sbt`:
     ```scala
     lazy val project_name = project
       .enablePlugins(CluePlugin)
       // other settings
     ```

2. **Directory Structure**:
   - GraphQL queries should be placed in `<project>/src/clue/scala/[package]/`

3. **Schema Reference Configuration**:
   - Create or update `.scalafix.conf` to reference GraphQL schemas:
     ```
     GraphQLGen.schemaDirs=["/lucuma/schemas"]  # /lucuma/schemas is in lucuma-schemas
     ```
     In this example the graphql schema is in a library
     Multiple directories can be specified:

4. **Dependencies (in case the schema is in a library)**:
   - Include the library in project dependencies, in this example lucuma-schemas
   - Add the scalafix dependency in `build.sbt`:
     ```scala
     ThisBuild / scalafixDependencies += "edu.gemini" % "lucuma-schemas_3" % LibraryVersions.lucumaSchemas
     ```

## How It Works

1. The Clue plugin generates source code in `target/scala-[version]/src_managed`
2. These generated sources are compiled along with regular code
3. Generated classes can be imported, like `import [package].SomeQueriesGQL`

## Common Issues

- Missing schema error: "No schema [SchemaName.graphql] found in paths []"
  - Solution: Add the correct schema directories in `.scalafix.conf`

## Verification

After setup, you should be able to see generated class files in:
```
modules/[project]/target/scala-[version]/src_managed/[package]/[Query]GQL.scala
```
