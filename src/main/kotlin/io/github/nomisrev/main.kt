package io.github.nomisrev

import io.github.nomisrev.config.Config
import io.github.nomisrev.config.Dependencies
import io.github.nomisrev.config.configure
import io.github.nomisrev.config.dependencies
import io.github.nomisrev.routes.healthRoute
import io.github.nomisrev.routes.userRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun main(): Unit =
  runBlocking(Dispatchers.Default) {
    val config = Config()
    dependencies(config).use { module ->
      embeddedServer(
          Netty,
          host = config.http.host,
          port = config.http.port,
        ) { app(module) }
        .start(wait = true)
    }
  }

fun Application.app(module: Dependencies) {
  configure()
  healthRoute(module.hikariDataSource)
  userRoutes(module.userPersistence, module.config.auth)
}
