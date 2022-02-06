package io.github.nomisrev

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ContentNegotiation
import kotlinx.serialization.json.Json

fun Application.setup() {
  install(ContentNegotiation) {
    json(
      Json {
        isLenient = true
        ignoreUnknownKeys = true
      }
    )
  }
  happyBirthdayRouting()
}
