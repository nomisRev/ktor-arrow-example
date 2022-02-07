package io.github.nomisrev

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.ContentNegotiation
import kotlinx.serialization.json.Json

fun main() {
  embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
    configure()
    happyBirthdayRouting()
  }.start(wait = true)
}

fun Application.configure() {
  install(ContentNegotiation) {
    json(
      Json {
        isLenient = true
        ignoreUnknownKeys = true
      }
    )
  }
}
