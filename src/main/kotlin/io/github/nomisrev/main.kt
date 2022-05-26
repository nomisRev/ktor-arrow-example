package io.github.nomisrev

import io.github.nomisrev.env.Env
import io.github.nomisrev.env.Dependencies
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.routes.userRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import kotlinx.coroutines.Dispatchers

fun main(): Unit = cancelOnShutdown(Dispatchers.Default) {
  val env = Env()
  dependencies(env).use { module ->
    embeddedServer(Netty, host = env.http.host, port = env.http.port) {
      app(module)
    }.start(wait = true)
  }
}

fun Application.app(module: Dependencies) {
  install(DefaultHeaders)
  install(ContentNegotiation) { json() }
  userRoutes(module.userService)
}
