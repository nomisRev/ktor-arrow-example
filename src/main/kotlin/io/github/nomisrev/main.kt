package io.github.nomisrev

import arrow.continuations.SuspendApp
import arrow.fx.coroutines.continuations.resource
import io.github.nomisrev.env.Dependencies
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.configure
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.routes.health
import io.github.nomisrev.routes.userRoutes
import io.ktor.server.application.Application
import io.ktor.server.netty.Netty
import kotlinx.coroutines.awaitCancellation

fun main(): Unit = SuspendApp {
  val env = Env()
  resource {
    val dependencies = dependencies(env).bind()
    val engine = server(Netty,  host = env.http.host, port = env.http.port).bind()
    engine.application.app(dependencies)
  }.use { awaitCancellation() }
}

fun Application.app(module: Dependencies) {
  configure()
  userRoutes(module.userService, module.jwtService)
  health()
}
