package io.github.nomisrev.config

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.fromCloseable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisrev.service.DatabasePool
import io.github.nomisrev.utils.queryOneOrNull

fun hikari(config: Config.DataSource): Resource<HikariDataSource> =
  Resource.fromCloseable {
    HikariDataSource(
      HikariConfig().apply {
        jdbcUrl = config.url
        username = config.username
        password = config.password
        driverClassName = config.driver
      }
    )
  }

fun databasePool(hikari: HikariDataSource) =
  object : DatabasePool {
    override fun isRunning(): Boolean = hikari.isRunning
    override suspend fun version(): String? =
      hikari.queryOneOrNull("SHOW server_version;") { string() }
  }
