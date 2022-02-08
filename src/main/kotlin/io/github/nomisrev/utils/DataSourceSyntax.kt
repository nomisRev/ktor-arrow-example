package io.github.nomisrev.utils

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.fromAutoCloseable
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource

fun DataSource.connection(): Resource<Connection> = Resource.fromAutoCloseable { connection }

fun DataSource.prepareStatement(
  sql: String,
  binders: (SqlPreparedStatement.() -> Unit)? = null
): Resource<PreparedStatement> =
  connection().flatMap { connection ->
    Resource.fromAutoCloseable {
      connection.prepareStatement(sql).apply {
        if (binders != null) SqlPreparedStatement(this).binders()
      }
    }
  }

suspend fun DataSource.query(sql: String): Unit =
  prepareStatement(sql)
    .flatMap { preparedStatement -> Resource({ preparedStatement.executeUpdate() }, { _, _ -> }) }
    .use {}

suspend fun <A> DataSource.queryOneOrNull(
  sql: String,
  binders: (SqlPreparedStatement.() -> Unit)? = null,
  mapper: SqlCursor.() -> A
): A? =
  prepareStatement(sql)
    .flatMap { preparedStatement ->
      Resource.fromAutoCloseable {
        preparedStatement
          .apply { if (binders != null) SqlPreparedStatement(this).binders() }
          .executeQuery()
      }
    }
    .use { rs -> if (rs.next()) mapper(SqlCursor(rs)) else null }

suspend fun <A> DataSource.queryAsList(
  sql: String,
  binders: (SqlPreparedStatement.() -> Unit)? = null,
  mapper: SqlCursor.() -> A?
): List<A> =
  prepareStatement(sql)
    .flatMap { preparedStatement ->
      Resource.fromAutoCloseable {
        preparedStatement
          .apply { if (binders != null) SqlPreparedStatement(this).binders() }
          .executeQuery()
      }
    }
    .use { rs ->
      val buffer = mutableListOf<A>()
      while (rs.next()) {
        mapper(SqlCursor(rs))?.let(buffer::add)
      }
      buffer
    }

class SqlPreparedStatement(private val preparedStatement: PreparedStatement) {
  private var index: Int = 1

  fun bind(short: Short?): Unit = bind(short?.toLong())
  fun bind(byte: Byte?): Unit = bind(byte?.toLong())
  fun bind(int: Int?): Unit = bind(int?.toLong())
  fun bind(char: Char?): Unit = bind(char?.toString())

  fun bind(bytes: ByteArray?): Unit =
    if (bytes == null) preparedStatement.setNull(index++, Types.BLOB)
    else preparedStatement.setBytes(index++, bytes)

  fun bind(long: Long?): Unit =
    if (long == null) preparedStatement.setNull(index++, Types.INTEGER)
    else preparedStatement.setLong(index++, long)

  fun bind(double: Double?): Unit =
    if (double == null) preparedStatement.setNull(index++, Types.REAL)
    else preparedStatement.setDouble(index++, double)

  fun bind(string: String?): Unit =
    if (string == null) preparedStatement.setNull(index++, Types.VARCHAR)
    else preparedStatement.setString(index++, string)
}

class SqlCursor(private val resultSet: ResultSet) {
  private var index: Int = 1
  fun int(): Int? = long()?.toInt()
  fun string(): String? = resultSet.getString(index++)
  fun bytes(): ByteArray? = resultSet.getBytes(index++)
  fun long(): Long? = resultSet.getLong(index++).takeUnless { resultSet.wasNull() }
  fun double(): Double? = resultSet.getDouble(index++).takeUnless { resultSet.wasNull() }
  fun nextRow(): Boolean = resultSet.next()
}
