package io.github.nomisrev.config

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.repo.userPersistence

class Dependencies(
  val config: Config,
  val userPersistence: UserPersistence
)

fun dependencies(config: Config): Resource<Dependencies> = resource {
  val hikari = hikari(config.dataSource).bind()
  val sqlDelight = sqlDelight(hikari).bind()
  val userPersistence = userPersistence(sqlDelight.usersQueries)
  Dependencies(config, userPersistence)
}
