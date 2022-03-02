package io.github.nomisrev

import io.github.nomisrev.config.Module
import io.github.nomisrev.config.configure
import io.github.nomisrev.config.envConfig
import io.github.nomisrev.config.module
import io.github.nomisrev.routes.healthRoute
import io.github.nomisrev.routes.userRoutes
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.CORS
import io.ktor.server.plugins.ContentNegotiation
import io.ktor.server.plugins.DefaultHeaders
import io.ktor.server.plugins.maxAgeDuration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

fun main(): Unit =
  runBlocking(Dispatchers.Default) {
    val config = envConfig()
    module(config).use { module ->
      embeddedServer(
          Netty,
          port = config.http.port,
          host = config.http.host,
          parentCoroutineContext = coroutineContext,
        ) { app(module) }
        .start(wait = true)
    }
  }

fun Application.app(module: Module) {
  configure()
  healthRoute(module.pool)
  userRoutes(module.userService)
}
