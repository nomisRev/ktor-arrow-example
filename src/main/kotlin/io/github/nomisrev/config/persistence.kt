package io.github.nomisrev.config

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.fromCloseable
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

fun sqlDelight(dataSource: DataSource): Resource<SqlDelight> = resource {
  val driver = Resource.fromCloseable(dataSource::asJdbcDriver).bind()
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
