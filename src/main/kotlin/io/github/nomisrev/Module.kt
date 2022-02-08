package io.github.nomisrev

import arrow.fx.coroutines.Resource

data class Module(val database: Database)

fun module(config: Config): Resource<Module> =
  hikari(config.dataSource).flatMap { hikari ->
    sqlDelight(hikari).map { sqlDelight -> Module(database(hikari)) }
  }
