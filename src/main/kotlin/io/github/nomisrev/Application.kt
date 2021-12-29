package io.github.nomisrev

import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.andThen
import arrow.core.invalidNel
import arrow.core.valid
import arrow.core.zip
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main() {
  embeddedServer(
    Netty,
    port = 8080,
    host = "0.0.0.0",
    module = Application::setup
  ).start(wait = true)
}

fun Application.setup() {
  install(ContentNegotiation) {
    json(Json {
      isLenient = true
      ignoreUnknownKeys = true
    })
  }
  happyBirthdayRouting()
}

fun Application.happyBirthdayRouting(): Routing =
  routing {
    get("/happy-birthday/{name}/{age}") {
      val validated = path("age") { "age not present" }
        .validateInt()
        .zip(path("name") { "name not present" }, ::Person)

      when (validated) {
        is Validated.Valid -> call.respond(HttpStatusCode.OK, validated.value)
        is Validated.Invalid -> call.respond(
          HttpStatusCode.BadRequest,
          validated.value.joinToString(prefix = "The following errors were found: ")
        )
      }
    }
  }

fun ValidatedNel<String, String>.validateInt(): ValidatedNel<String, Int> =
  andThen {
    it.toIntOrNull()?.valid() ?: "$it is not a number".invalidNel()
  }

fun <E> PipelineContext<*, ApplicationCall>.query(
  name: String,
  notPresent: () -> E
): ValidatedNel<E, String> =
  call.request.queryParameters[name]?.valid() ?: notPresent().invalidNel()

fun <E> PipelineContext<*, ApplicationCall>.path(
  name: String,
  notPresent: () -> E
): ValidatedNel<E, String> =
  call.parameters[name]?.valid() ?: notPresent().invalidNel()

@Serializable
data class Person(val age: Int, val name: String)
