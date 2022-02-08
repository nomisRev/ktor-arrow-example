package io.github.nomisrev

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.ContentNegotiation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

fun main(): Unit =
  runBlocking(Dispatchers.Default) {
    val config = envConfig()
    module(config).use { module ->
      embeddedServer(
          Netty,
          port = config.port,
          host = config.host,
          parentCoroutineContext = coroutineContext,
        ) { app(module) }
        .start(wait = true)
    }
  }

fun Application.app(module: Module) {
  configure()
  healthRoute(module.database)
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
