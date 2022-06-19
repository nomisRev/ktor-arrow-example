package io.github.nomisrev.utils

import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.addShutdownHook
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job

/**
 * Way of gracefully shutting down Ktor services through `embeddedServer` whilst respecting
 * the compositional properties of the surrounding suspending code.
 *
 * When we need to shut down a Ktor service we need to properly take into account a _grace_ period where we still handle
 * requests instead of immediately cancelling any in-flight requests.
 *
 * We also need to add a _prewait_ for allowing any Ingress or any Load Balancers to de-register our service.
 * https://philpearl.github.io/post/k8s_ingress/
 *
 * @param prewait a duration to wait before beginning the stop process. During this time, requests will continue
 * to be accepted. This setting is useful to allow time for the container to be removed from the load balancer.
 * This is disabled when `io.ktor.development=true`.
 *
 * @param grace a duration during which already inflight requests are allowed to continue before the
 * shutdown process begins.
 *
 * @param timeout a duration after which the server will be forceably shutdown.
 *
 * ```kotlin
 * fun main(): Unit = runBlocking(Dispatchers.Default) {
 *   embeddedServer(Netty, port = 8080) {
 *     routing { get("ping") { call.respond("pong") } }
 *   }.awaitShutdown()
 * }
 * ```
 */
context(scope@CoroutineScope)
suspend fun ApplicationEngine.awaitShutdown(
  prewait: Duration = 30.seconds,
  grace: Duration = 1.seconds,
  timeout: Duration = 5.seconds,
): Unit {
  addShutdownHook {
    // We use a CountDownLatch to back-pressure JVM exit
    val countDownLatch = CountDownLatch(1)
    suspend {
      if (!environment.developmentMode) {
        environment.log.info(
          "prewait delay of ${prewait.inWholeMilliseconds}ms, turn it off using io.ktor.development=true"
        )
        // Safe since we're on KtorShutdownHook Thread. Avoids additional shifting
        Thread.sleep(prewait.inWholeMilliseconds)
      }
      environment.log.info("Shutting down HTTP server...")
      stop(grace.inWholeMilliseconds, timeout.inWholeMilliseconds)
      environment.log.info("HTTP server shutdown!")
      this@scope.coroutineContext.job.join()
      countDownLatch.countDown()
    }.startCoroutineUninterceptedOrReturn(Continuation(EmptyCoroutineContext, Result<Unit>::getOrThrow))
    countDownLatch.await(
      prewait.inWholeMilliseconds + grace.inWholeMilliseconds + (2 * timeout.inWholeMilliseconds),
      TimeUnit.MILLISECONDS
    )
  }
  start(wait = true)
}
