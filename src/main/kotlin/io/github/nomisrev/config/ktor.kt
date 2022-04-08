package io.github.nomisrev.config

import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.CORS
import io.ktor.server.plugins.cors.maxAgeDuration
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.Json

fun Application.configure() {
  install(DefaultHeaders)
  install(ContentNegotiation) {
    json(
      Json {
        isLenient = true
        ignoreUnknownKeys = true
      }
    )
  }
  install(CORS) {
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowNonSimpleContentTypes = true
    maxAgeDuration = 3.days
  }
}
