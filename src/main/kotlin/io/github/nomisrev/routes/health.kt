package io.github.nomisrev.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.health() = routing {
  get("health") {
    call.respond(HttpStatusCode.OK)
  }
}
