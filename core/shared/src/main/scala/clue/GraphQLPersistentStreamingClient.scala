// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

trait GraphQLPersistentStreamingClient[F[_], S]
    extends GraphQLStreamingClient[F, S]
    with PersistentClient[F]
