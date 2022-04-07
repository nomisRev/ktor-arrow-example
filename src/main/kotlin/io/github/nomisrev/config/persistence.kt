package io.github.nomisrev.config

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.fromCloseable
import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.sqlite.driver.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisrev.sqldelight.Articles
import io.github.nomisrev.sqldelight.Comments
import io.github.nomisrev.sqldelight.SqlDelight
import java.time.OffsetDateTime
import javax.sql.DataSource

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

fun sqlDelight(dataSource: DataSource): Resource<SqlDelight> =
  Resource.fromCloseable(dataSource::asJdbcDriver).map { driver ->
    SqlDelight.Schema.create(driver)
    SqlDelight(
      driver,
      Articles.Adapter(OffsetDateTimeAdapter, OffsetDateTimeAdapter),
      Comments.Adapter(OffsetDateTimeAdapter, OffsetDateTimeAdapter)
    )
  }

private object OffsetDateTimeAdapter : ColumnAdapter<OffsetDateTime, String> {
  override fun decode(databaseValue: String): OffsetDateTime = OffsetDateTime.parse(databaseValue)

  override fun encode(value: OffsetDateTime): String = value.toString()
}
