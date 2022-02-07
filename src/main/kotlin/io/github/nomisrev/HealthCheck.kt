package io.github.nomisrev

import io.ktor.server.application.Application
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.healthRoute(database: Database): Routing = routing {
  get("/health") {
    if (database.isRunning()) ok("true") else serverError("false")
  }
}