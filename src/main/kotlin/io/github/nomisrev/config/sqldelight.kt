package io.github.nomisrev.config

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.fromCloseable
import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.sqlite.driver.asJdbcDriver
import io.github.nomisrev.sqldelight.Articles
import io.github.nomisrev.sqldelight.Comments
import io.github.nomisrev.sqldelight.SqlDelight
import java.time.LocalDateTime
import javax.sql.DataSource

fun sqlDelight(dataSource: DataSource): Resource<SqlDelight> =
  Resource.fromCloseable(dataSource::asJdbcDriver).map { driver ->
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
