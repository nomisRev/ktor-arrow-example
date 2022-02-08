package io.github.nomisrev

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.fromCloseable
import com.squareup.sqldelight.sqlite.driver.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisrev.utils.queryOneOrNull

interface Database {
  fun isRunning(): Boolean
  suspend fun version(): String?
}

fun database(hikari: HikariDataSource): Database =
  object : Database {
    override fun isRunning(): Boolean = hikari.isRunning
    override suspend fun version(): String? =
      hikari.queryOneOrNull("SHOW server_version;") { string() }
  }

fun sqlDelight(hikariDataSource: HikariDataSource): Resource<SqlDelight> =
  Resource.fromCloseable { hikariDataSource.asJdbcDriver() }.map { driver -> SqlDelight(driver) }

fun hikari(config: DataSource): Resource<HikariDataSource> =
  Resource.fromCloseable {
    HikariDataSource(
      HikariConfig().apply {
        jdbcUrl = config.url
        username = config.username
        password = config.password
        driverClassName = config.driver
        maximumPoolSize = config.maximumPoolSize
      }
    )
  }
