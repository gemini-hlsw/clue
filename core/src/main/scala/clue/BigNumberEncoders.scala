// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import io.circe.Encoder

// Long, BigInt and BigDecimal types can exceed the size of the graphQL number types,
// so we need to send them quoted.
// To use these encoders instead of the default circe ones, in your GraphQLSchema file,
// include `// gql: import clue.BigNumberEncoders._`
// For some reason, Sangria seemed to be able to handle them unquoted, but Grackle can't.
object BigNumberEncoders {
  given Encoder[Long]       = Encoder.encodeString.contramap[Long](_.toString)
  given Encoder[BigInt]     = Encoder.encodeString.contramap[BigInt](_.toString)
  given Encoder[BigDecimal] = Encoder.encodeString.contramap[BigDecimal](_.toString)
}
