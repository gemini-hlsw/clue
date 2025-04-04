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
   - GraphQL queries should be placed in `<project>/src/clue/scala/[package]/queries/`

3. **Schema Reference Configuration**:
   - Create or update `.scalafix.conf` to reference GraphQL schemas:
     ```
     GraphQLGen.schemaDirs=["/lucuma/schemas"]  # /lucuma/schemas is in lucuma-schemas
     ```
     In this example the graphql schema is in a library

4. **Dependencies**:
   - Include the library in project dependencies, in this example lucuma-schemas
   - Add the scalafix dependency in `build.sbt`:
     ```scala
     ThisBuild / scalafixDependencies += "edu.gemini" % "lucuma-schemas_3" % LibraryVersions.lucumaSchemas
     ```

## How It Works

1. The Clue plugin generates source code in `target/scala-[version]/src_managed`
2. These generated sources are compiled along with regular code
3. Generated classes can be imported, like `import [package].queries.SomeQueriesGQL`

## Common Issues

- Missing schema error: "No schema [SchemaName.graphql] found in paths []"
  - Solution: Add the correct schema directories in `.scalafix.conf`
- Import not found error: "value queries is not a member of [package]"
  - Solution: Check correct directory structure and ensure plugin is generating the sources

## Verification

After setup, you should be able to see generated class files in:
```
modules/[project]/target/scala-[version]/src_managed/[package]/queries/[Query]GQL.scala
```
