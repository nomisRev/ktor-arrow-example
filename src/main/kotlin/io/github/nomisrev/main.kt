package io.github.nomisrev

import io.github.nomisrev.env.Env
import io.github.nomisrev.env.Dependencies
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.routes.userRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.addShutdownHook
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.engine.stopServerOnCancellation
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.runBlocking
import sun.misc.Signal

fun main(): Unit = cancelOnSigint(Dispatchers.Default) {
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

/**
 * Alternative to runBlocking which cancels the inner coroutine on SIGINT.
 * Take-care, if the inner coroutine is non-cancellable,
 * then SIGINT might not shut down JVM until SIGKILL is killed.
 *
 * fun main(): Unit = cancelOnSigint {
 *   withContext(NonCancelable) {
 *      delay(10_000)
 *      println("Finally closing")
 *   }
 * }
 *
 * If for the code above we call SIGINT after 1 seconds,
 * then cancelOnSigint will still back-pressure actual closing for 9 seconds and
 * "Finally closing" will be printed.
 */
private fun cancelOnSigint(
  ctx: CoroutineContext = EmptyCoroutineContext,
  f: suspend CoroutineScope.() -> Unit
) = runBlocking(ctx) {
  val job = launch(start = CoroutineStart.LAZY) { f() }
  Signal.handle(Signal("INT")) { job.cancel() }
  job.start()
  job.join()
}
