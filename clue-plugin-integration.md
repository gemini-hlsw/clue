# How to setup the Clue Plugin

`sbt-clue` is cross-built for sbt 1.x and sbt 2.x.

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
     Clue.schemaDirs=["/lucuma/schemas"]  # /lucuma/schemas is in lucuma-schemas
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

1. The Clue plugin generates source code in the `src_managed` directory of the project. On sbt 1.x
   this is `<project>/target/scala-[version]/src_managed`. On sbt 2.x it is
   `target/out/jvm/scala-[version]/[project]/src_managed`.
2. These generated sources are compiled along with regular code
3. Generated classes can be imported, like `import [package].SomeQueriesGQL`

## Validating hand-written operations

The generator validates the annotated operations/subqueries under `src/clue/scala`. Hand-written ones
elsewhere in the project (e.g. `@GraphQLType("Type") object Foo extends GraphQLSubquery.Typed[Schema, T]`
in `src/main`) are validated **automatically on every compile**: the plugin decorates the `compile`
task to run the real compilation first and then invoke the `GraphQLValidate` rule over it, so an
invalid query fails the build. `<project>/clueCheck` runs the same validation on demand (e.g. in CI).
On-compile validation can be turned off with `clueValidateOnCompile := false`.

A misplaced `@GraphQL`/`@GraphQLSchema`/`@GraphQLStub` annotation outside `src/clue/scala` is reported
as a warning (those are only processed by the generator). Do not add `rules = [GraphQLGen]` to
`.scalafix.conf`: a plain `scalafixAll` would then expand the annotated generator inputs in place.

## Common Issues

- Missing schema error: "No schema [SchemaName.graphql] found in paths []"
  - Solution: Add the correct schema directories in `.scalafix.conf`

## Verification

After setup, you should be able to see generated class files in:
```
# sbt 1.x
modules/[project]/target/scala-[version]/src_managed/[package]/[Query]GQL.scala
# sbt 2.x
target/out/jvm/scala-[version]/[project]/src_managed/[package]/[Query]GQL.scala
```
