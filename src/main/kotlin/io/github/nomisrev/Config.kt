package io.github.nomisrev

import arrow.fx.coroutines.Resource

data class Config(val database: DataSource, val port: Int = 8080)

data class DataSource(
  val url: String,
  val username: String,
  val password: String,
  val driver: String = "org.postgresql.Driver",
  val maximumPoolSize: Int = 10
)

data class Module(val database: Database)

fun module(config: Config): Resource<Module> =
  hikari(config.database).flatMap { hikari ->
    sqlDelight(hikari).map { sqlDelight -> Module(database(hikari)) }
  }
