package io.github.nomisrev.env

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.closeable
import arrow.fx.coroutines.continuations.ResourceScope
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisrev.repo.ArticleId
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.sqldelight.Articles
import io.github.nomisrev.sqldelight.Comments
import io.github.nomisrev.sqldelight.SqlDelight
import io.github.nomisrev.sqldelight.Tags
import io.github.nomisrev.sqldelight.Users
import java.time.OffsetDateTime
import javax.sql.DataSource

suspend fun ResourceScope.hikari(env: Env.DataSource): HikariDataSource = autoCloseable {
  HikariDataSource(
    HikariConfig().apply {
      jdbcUrl = env.url
      username = env.username
      password = env.password
      driverClassName = env.driver
    }
  )
}

suspend fun ResourceScope.sqlDelight(dataSource: DataSource): SqlDelight {
  val driver = closeable { dataSource.asJdbcDriver() }
  SqlDelight.Schema.create(driver)
  return SqlDelight(
    driver,
    Articles.Adapter(articleIdAdapter, userIdAdapter, offsetDateTimeAdapter, offsetDateTimeAdapter),
    Comments.Adapter(offsetDateTimeAdapter, offsetDateTimeAdapter),
    Tags.Adapter(articleIdAdapter),
    Users.Adapter(userIdAdapter)
  )
}

private val articleIdAdapter = columnAdapter(::ArticleId, ArticleId::serial)
private val userIdAdapter = columnAdapter(::UserId, UserId::serial)
private val offsetDateTimeAdapter = columnAdapter(OffsetDateTime::parse, OffsetDateTime::toString)

private inline fun <A : Any, B> columnAdapter(
  crossinline decode: (databaseValue: B) -> A,
  crossinline encode: (value: A) -> B
): ColumnAdapter<A, B> =
  object : ColumnAdapter<A, B> {
    override fun decode(databaseValue: B): A = decode(databaseValue)

    override fun encode(value: A): B = encode(value)
  }
