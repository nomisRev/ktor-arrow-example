package io.github.nomisrev.service

import com.zaxxer.hikari.HikariDataSource
import io.github.nomisrev.utils.queryOneOrNull

interface DatabasePool {
  fun isRunning(): Boolean
  suspend fun version(): String?
}

fun databasePool(hikari: HikariDataSource) =
  object : DatabasePool {
    override fun isRunning(): Boolean = hikari.isRunning
    override suspend fun version(): String? =
      hikari.queryOneOrNull("SHOW server_version;") { string() }
  }
