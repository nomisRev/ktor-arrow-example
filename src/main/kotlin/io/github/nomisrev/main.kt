package io.github.nomisrev

import io.github.nomisrev.env.Dependencies
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.configure
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.routes.userRoutes
import io.github.nomisrev.utils.awaitShutdown
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
      ) { app(module) }.awaitShutdown()
    }
  }

fun Application.app(module: Dependencies) {
  configure()
  userRoutes(module.userService, module.jwtService)
}
