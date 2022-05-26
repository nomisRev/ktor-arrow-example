package io.github.nomisrev

import java.sql.PreparedStatement
import javax.sql.DataSource

fun DataSource.query(sql: String): Unit =
  connection.use { connection ->
    connection.prepareStatement(sql)
      .use(PreparedStatement::executeUpdate)
  }
