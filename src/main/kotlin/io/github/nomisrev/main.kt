package io.github.nomisrev

import io.github.nomisrev.config.Env
import io.github.nomisrev.config.Dependencies
import io.github.nomisrev.config.configure
import io.github.nomisrev.config.dependencies
import io.github.nomisrev.routes.userRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun main(): Unit =
  runBlocking(Dispatchers.Default) {
    val env = Env()
    dependencies(env).use { module ->
      embeddedServer(
          Netty,
          host = env.http.host,
          port = env.http.port,
        ) { app(module) }
        .start(wait = true)
    }
  }

fun Application.app(module: Dependencies) {
  configure()
  with(module.userPersistence, module.env.auth) {
    userRoutes()
  }
}
