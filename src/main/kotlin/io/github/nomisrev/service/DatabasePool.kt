package io.github.nomisrev.service

interface DatabasePool {
  fun isRunning(): Boolean
  suspend fun version(): String?
}
