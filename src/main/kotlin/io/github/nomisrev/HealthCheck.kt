package io.github.nomisrev

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.computations.ensureNotNull
import io.github.nomisrev.utils.ok
import io.github.nomisrev.utils.serverError
import io.ktor.server.application.Application
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable data class HealthCheck(val postgresVersion: String)

fun Application.healthRoute(database: Database): Routing = routing {
  get("/health") {
    val res =
      either<Unit, HealthCheck> {
        ensure(database.isRunning()) {}
        val version = ensureNotNull(database.version()) {}
        HealthCheck(version)
      }

    when (res) {
      is Either.Right -> ok(res.value)
      is Either.Left -> serverError("not healthy")
    }
  }
}
