package io.github.nomisrev.config

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.repo.userPersistence

class Dependencies(val env: Env, val userPersistence: UserPersistence)

fun dependencies(env: Env): Resource<Dependencies> = resource {
  val hikari = hikari(env.dataSource).bind()
  val sqlDelight = sqlDelight(hikari).bind()
  val userPersistence = userPersistence(sqlDelight.usersQueries)
  Dependencies(env, userPersistence)
}
