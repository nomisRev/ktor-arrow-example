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
    effect<Unit, HealthCheck> {
      healthCheck()
    }.fold(
      { call.respond(HttpStatusCode.ServiceUnavailable) },
      { call.respond(HttpStatusCode.OK, it) }
    )
  }
}

context(HikariDataSource, EffectScope<Unit>)
private suspend fun healthCheck(): HealthCheck {
  val version = ensureNotNull(showPostgresVersion()) { }
  ensure(isRunning) { }
  isRunning
  return HealthCheck(version)
}

context(DataSource)
private suspend fun showPostgresVersion(): String? =
  queryOneOrNull("SHOW server_version;") { string() }
