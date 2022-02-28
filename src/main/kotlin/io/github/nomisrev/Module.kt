package io.github.nomisrev

import arrow.fx.coroutines.Resource
import io.github.nomisrev.service.UserService
import io.github.nomisrev.service.userService

data class Module(val pool: DatabasePool, val userService: UserService)

fun module(config: Config): Resource<Module> =
  hikari(config.dataSource).flatMap { hikari ->
    sqlDelight(hikari).map { sqlDelight ->
      Module(databasePool(hikari), userService(config.auth, sqlDelight.usersQueries))
    }
  }
