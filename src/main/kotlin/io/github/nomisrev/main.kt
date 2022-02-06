package io.github.nomisrev

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/** Java Application main */
fun main() {
  embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::setup)
    .start(wait = true)
}
