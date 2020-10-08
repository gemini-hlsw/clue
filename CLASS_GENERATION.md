# Automatic class generation

## Why?

In an application with a lot of ad hoc GraphQL operations, we will have to define classes to hold every possible result, plus their Json `Decoder` instances. Moreover, there may also be a lot of `input` and `enum` types, plus `Encoder` instances for `input` types and both `Encoder` and `Decoder` instances for `enum` types. On top of that, we may event want to define additional typeclass instances (eg: `Eq`, `Show`) or other structure-derived members (eg: lenses) for these classes.

Writing all this boilerplate can get tedious pretty fast. And since all the information to write all this boilerplate is already in the schema and the operations, why not use it?

We identified 4 possible approaches to accomplish this. Of these, **annotations macros** have been implemented and proved to work as expected, but unfortunately that is not an option in Scala 3, at least for the moment.

The 4 possible approaches are: 

## 1) Standalone source generator

In this approach, all GraphQL operations are written in `.graphql` files. Given a file with a schema, a process can be run manually which :
 - Generates a `Schema.scala` file containing definitions for `input` and `enum` types (plus `Encoder`/`Decoder` and optionally other typeclass instances + lenses).
 - Goes through each `.graphql` file and generates a `.scala` file containing `object X extends GraphQLOperation[Schema]` with types to hold the result (plus `Decoder` and optionally other typeclass instances + lenses) and to provide operation parameters (plus `Encoder` and optionally other typeclass instances + lenses).

 The process can be a standalone executable or a task in an sbt plugin that leverages its managed sources mechanism.

**Pros:**
- All code is explicit.
- Works everywhere (Although if it's an sbt plugin, it will only work with that build tool. Also, we need to generate different code for Scala 2 and 3).

**Cons:**
- We need an extra configuration file to provide metadata. In particular:
  - Mappings for `scalar` types.
  - Mappings for types we might have already defined in our code.
  - Alternate `Encoder` or `Decoder` instances (might be convoluted to declare this).
- No part of this metadata is type checked by the compiler.
- Logic gets disseminated between `.graphql` and `.scala` files. Eg: To check from Scala code the actual GraphQL query that is being performed, the programmer has to manually go looking for the originating `.graphql` file.
- Code can get out of sync/tedious to run generator while developing.

## 2) Annotation macros

In this approach, we provide two macros: `@GraphQLSchema` and `@GraphQL`.

`@GraphQLSchema` can be applied to any `object`. It will look for a schema file with the same name as the object and extension `.graphql` and generates definitions for `input` and `enum` types (plus `Encoder`/`Decoder` and optionally other typeclass instances + lenses within the `object`. Definitions for `scalar` types, as well as overrides, can be defined within the `object`.

`@GraphQL` can be applied to any `object` extending `GraphQLOperation[Schema]`. That `Schema` can be any object that was annotated with `@GraphQLSchema`. The `object` just has to provide `val document` and the macro will generate types to hold the result (plus `Decoder` and optionally other typeclass instances + lenses) and to provide operation parameters (plus `Encoder` and optionally other typeclass instances + lenses).

Examples of this approach can be found in the tests of the `macros` subproject.

**Pros:**
- Already done :).
- All mappings and overrides are Scala code.
- No manual intervention necessary.

**Cons:**
- Generated code is not explicit. However, code can be shown during compilation, and even integrated into and IDE (currently the `debug=true` parameter of both macros will show the generated code as a tooltip in VSCode).
- Doesn't work in Scala 3 (at least in its first release.)

## 3) Scalafix + source generator

This approach is a bit of a mixture of the previous 2 approaches. Code would look pretty much the same as in approach **#2**. Then a Scalafix process is run that generates the missing types + typeclass instances within `trait`s in new files, and the current code is modified to extend those traits.

**Pros:**
- All code is explicit.
- All mappings and overrides are Scala code.
- Works everywhere Scalafix works. (We need to generate different code for Scala 2 and 3.)

**Cons:**
- Code can get out of sync/tedious to run Scalafix while developing.

## 4) Compiler plugin

With this approach we can come close to making GraphQL a first-class citizen. Everything would work pretty much the same as in approach **#2**, except that code generation is peformed by a plugin (which are available in Scala 3) instead of an annotation macro.

I don't know if quasiquotes can be used in a compiler plugin. If that's the case, we can probably reuse most of the code we have now for annotation macros. We would still have to change the flavor of the generated code to Scala 3.

**Pros:**
- All mappings and overrides are Scala code.
- No manual intervention necessary.

**Cons:**
- Generated code is not explicit. I don't know if there's a way to reveal what is being generated (other than spitting it out to the console while compiling).
- We would need different plugins for Scala 2 and 3. Or just provide a plugin for Scala 3 and continue using annotation macros for Scala 2.