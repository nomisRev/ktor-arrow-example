package io.github.nomisrev

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import io.github.nomisrev.env.Dependencies
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.configure
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.routes.articleRoutes
import io.github.nomisrev.routes.health
import io.github.nomisrev.routes.tagRoutes
import io.github.nomisrev.routes.userRoutes
import io.ktor.server.application.Application
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.awaitCancellation

fun main(): Unit = SuspendApp {
  val env = Env()
  resourceScope {
    val dependencies = dependencies(env)
    server(Netty, host = env.http.host, port = env.http.port) { app(dependencies) }
    awaitCancellation()
  }
}

fun Application.app(module: Dependencies) {
  configure()
  routing { userRoutes(module.userService, module.jwtService) }
  health(module.healthCheck)
  tagRoutes(module.tagPersistence)
  articleRoutes(module.articleService, module.jwtService)
}
