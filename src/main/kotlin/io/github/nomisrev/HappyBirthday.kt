package io.github.nomisrev

import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.andThen
import arrow.core.invalidNel
import arrow.core.valid
import arrow.core.zip
import io.ktor.server.application.Application
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable data class HappyBirthday(val age: Int, val name: String)

fun Application.happyBirthdayRouting(): Routing = routing {
  get("/happy-birthday/{name}/{age}") {
    val validated =
      path("age") { "age not present" }
        .validateInt()
        .zip(path("name") { "name not present" }, ::Person)

    when (validated) {
      is Validated.Valid -> ok(validated.value)
      is Validated.Invalid ->
        badRequest(validated.value.joinToString(prefix = "The following errors were found: "))
    }
  }
}

private fun ValidatedNel<String, String>.validateInt(): ValidatedNel<String, Int> = andThen {
  it.toIntOrNull()?.valid() ?: "$it is not a number".invalidNel()
}
