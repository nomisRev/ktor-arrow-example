package io.github.nomisrev

import arrow.core.Either
import io.github.nomisrev.utils.ok
import io.github.nomisrev.utils.serverError
import io.ktor.server.application.Application
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable data class HealthCheck(val postgresVersion: String)

fun Application.healthRoute(pool: DatabasePool): Routing = routing {
  get("/health") {
    when (val res = pool.healthCheck()) {
      is Either.Right -> ok(res.value)
      is Either.Left -> serverError(res.value)
    }
  }
}
