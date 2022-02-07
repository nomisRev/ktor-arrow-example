package io.github.nomisrev

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.fromCloseable
import com.squareup.sqldelight.sqlite.driver.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

interface Database {
  fun isRunning(): Boolean
}

fun database(hikari: HikariDataSource, sqlDelight: SqlDelight): Database =
  object : Database {
    override fun isRunning(): Boolean = hikari.isRunning
  }

fun sqlDelight(hikariDataSource: HikariDataSource): Resource<SqlDelight> =
  Resource.fromCloseable { hikariDataSource.asJdbcDriver() }
    .map { driver -> SqlDelight(driver) }

fun hikari(config: DataSource): Resource<HikariDataSource> = Resource.fromCloseable {
  HikariDataSource(HikariConfig().apply {
    jdbcUrl = config.url
    username = config.username
    password = config.password
    driverClassName = config.driver
    maximumPoolSize = config.maximumPoolSize
  })
}
