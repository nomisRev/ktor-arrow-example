package io.github.nomisrev

import io.kotest.core.spec.style.FreeSpec
import kotlinx.coroutines.runBlocking

/**
 * A Kotest Spec that allows suspension in its initializer. This works great for the
 * `ProjectResource`, for initialising dependencies.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class SuspendFun(body: suspend FreeSpec.() -> Unit) : FreeSpec() {
  init {
    runBlocking { body() }
  }
}
