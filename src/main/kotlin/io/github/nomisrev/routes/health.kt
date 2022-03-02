@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.routes

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.computations.ensureNotNull
import io.github.nomisrev.service.DatabasePool
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable data class HealthCheck(val postgresVersion: String)

fun Application.healthRoute(pool: DatabasePool): Routing = routing {
  get("/health") {
    when (val res = pool.healthCheck()) {
      is Either.Right -> call.respond(HttpStatusCode.OK, res.value)
      is Either.Left -> call.respond(HttpStatusCode.InternalServerError, res.value)
    }
  }
}

suspend fun DatabasePool.healthCheck(): Either<String, HealthCheck> = either {
  ensure(isRunning()) { "DatabasePool is not running" }
  val version = ensureNotNull(version()) { "Could not reach database. ConnectionPool is running." }
  HealthCheck(version)
}
