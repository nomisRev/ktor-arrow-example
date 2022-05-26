package io.github.nomisrev.env

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import io.github.nomisrev.persistence.userPersistence
import io.github.nomisrev.service.UserService
import io.github.nomisrev.service.JwtService
import io.github.nomisrev.service.userService

class Dependencies(val userService: UserService)

fun dependencies(env: Env): Resource<Dependencies> = resource {
  val hikari = hikari(env.dataSource).bind()
  val sqlDelight = sqlDelight(hikari).bind()
  val userPersistence = userPersistence(sqlDelight.usersQueries)
  Dependencies(userService(userPersistence, JwtService(env.auth)))
}
