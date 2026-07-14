package io.github.nomisrev.env

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import arrow.core.NonEmptyList
import arrow.core.raise.context.Raise
import arrow.core.raise.recover
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.closeable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisrev.Body
import io.github.nomisrev.Description
import io.github.nomisrev.Title
import io.github.nomisrev.articles.ArticleId
import io.github.nomisrev.articles.Slug
import io.github.nomisrev.sqldelight.Articles
import io.github.nomisrev.sqldelight.SqlDelight
import io.github.nomisrev.sqldelight.Tags
import io.github.nomisrev.sqldelight.Users
import io.github.nomisrev.users.UserId
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
        Articles.Adapter(
            articleIdAdapter,
            slugAdapter,
            titleAdapter,
            descriptionAdapter,
            bodyAdapter,
            userIdAdapter,
        ),
        Tags.Adapter(articleIdAdapter),
        Users.Adapter(userIdAdapter),
    )
}

private val articleIdAdapter = columnAdapter(ArticleId::serial, ::ArticleId)
private val userIdAdapter = columnAdapter(UserId::serial, ::UserId)
private val slugAdapter = columnAdapter(Slug::value, ::Slug)

fun <Error, A> requireAll(transform: (Error) -> NonEmptyList<String>, block: Raise<Error>.() -> A) =
    recover(block) { error ->
        val messages = transform(error)
        throw IllegalArgumentException(messages.joinToString())
    }

private val titleAdapter =
    columnAdapter(Title::value) {
        requireAll({ it.errors }) { Title(it) }
    }
private val descriptionAdapter =
    columnAdapter(Description::value) {
        requireAll({ it.errors }) { Description(it) }
    }
private val bodyAdapter =
    columnAdapter(Body::value) {
        requireAll({ it.errors }) { Body(it) }
    }

private inline fun <A : Any, B> columnAdapter(
    crossinline encode: (value: A) -> B,
    crossinline decode: (databaseValue: B) -> A,
): ColumnAdapter<A, B> =
    object : ColumnAdapter<A, B> {
        override fun decode(databaseValue: B): A = decode(databaseValue)

        override fun encode(value: A): B = encode(value)
    }
