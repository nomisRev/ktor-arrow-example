@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.routes

import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisrev.ensureNotNull
import io.github.nomisrev.utils.queryOneOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import javax.sql.DataSource
import kotlinx.serialization.Serializable

@Serializable data class HealthCheck(val postgresVersion: String)

context(Application, HikariDataSource)
fun healthRoute(): Routing = routing {
  get("/health") {
    effect<String, HealthCheck> {
      healthCheck()
    }.fold(
      { internal(it) },
      { call.respond(HttpStatusCode.OK, it) }
    )
  }
}

context(HikariDataSource, EffectScope<String>)
private suspend fun healthCheck(): HealthCheck {
  ensure(isRunning) { "DatabasePool is not running" }
  val version = ensureNotNull(showPostgresVersion()) { "Could not reach database. ConnectionPool is running." }
  return HealthCheck(version)
}

context(DataSource)
private suspend fun showPostgresVersion(): String? =
  queryOneOrNull("SHOW server_version;") { string() }
