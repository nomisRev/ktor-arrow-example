package io.github.nomisrev

import io.github.nomisrev.env.Env

/**
 * A singleton `PostgreSQLContainer` Test Container. There is no need to `close` or `stop` the
 * test-container since the lifecycle is controlled by TC Ryuk container.
 *
 * ```kotlin
 * class TestClass : StringSpec({
 *   val postgres = PostgreSQLContainer.create()
 *   ...
 * })
 * ```
 *
 * // https://www.testcontainers.org/test_framework_integration/manual_lifecycle_control/
 */
class PostgreSQLContainer private constructor() :
  org.testcontainers.containers.PostgreSQLContainer<Nothing>("postgres:14.1-alpine") {

  fun config(): Env.DataSource = Env.DataSource(jdbcUrl, username, password, driverClassName)

  companion object {
    fun create(): PostgreSQLContainer = instance

    fun config(): Env.DataSource = instance.config()

    private val instance by lazy { PostgreSQLContainer().also { it.start() } }
  }
}
