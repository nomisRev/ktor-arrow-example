package io.github.nomisrev

import arrow.continuations.SuspendApp
import arrow.fx.coroutines.resourceScope
import io.github.nomisrev.env.Dependencies
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.configure
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.routes.health
import io.github.nomisrev.routes.routes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*

fun main() = SuspendApp {
  val env = Env()
  resourceScope {
    val dependencies = dependencies(env)
    embeddedServer(Netty, host = env.http.host, port = env.http.port) { app(dependencies) }
    awaitCancellation()
  }
}

fun Application.app(module: Dependencies) {
  configure()
  routes(module)
  health(module.healthCheck)
}
