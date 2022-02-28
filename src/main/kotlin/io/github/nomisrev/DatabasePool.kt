package io.github.nomisrev

import arrow.core.computations.either
import arrow.core.computations.ensureNotNull
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.fromCloseable
import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.sqlite.driver.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisrev.sqldelight.Articles
import io.github.nomisrev.sqldelight.Comments
import io.github.nomisrev.sqldelight.SqlDelight
import io.github.nomisrev.utils.queryOneOrNull
import java.time.LocalDateTime

interface DatabasePool {
  fun isRunning(): Boolean
  suspend fun version(): String?

  suspend fun healthCheck() =
    either<String, HealthCheck> {
      ensure(isRunning()) { "DatabasePool is not running" }
      val version =
        ensureNotNull(version()) { "Could not reach database. ConnectionPool is running." }
      HealthCheck(version)
    }
}

fun databasePool(hikari: HikariDataSource) =
  object : DatabasePool {
    override fun isRunning(): Boolean = hikari.isRunning
    override suspend fun version(): String? =
      hikari.queryOneOrNull("SHOW server_version;") { string() }
  }

fun sqlDelight(hikariDataSource: HikariDataSource): Resource<SqlDelight> =
  Resource.fromCloseable { hikariDataSource.asJdbcDriver() }.map { driver ->
    SqlDelight.Schema.create(driver)
    SqlDelight(
      driver,
      Articles.Adapter(LocalDateTimeAdapter, LocalDateTimeAdapter),
      Comments.Adapter(LocalDateTimeAdapter, LocalDateTimeAdapter)
    )
  }

private object LocalDateTimeAdapter : ColumnAdapter<LocalDateTime, String> {
  override fun decode(databaseValue: String): LocalDateTime = LocalDateTime.parse(databaseValue)

  override fun encode(value: LocalDateTime): String = value.toString()
}

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
