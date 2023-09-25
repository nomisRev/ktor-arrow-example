package io.github.nomisrev

import arrow.fx.coroutines.continuations.resource
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.env.hikari
import io.kotest.assertions.arrow.fx.coroutines.ProjectResource
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.extensions.testcontainers.StartablePerProjectListener
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait

private class PostgreSQL : PostgreSQLContainer<PostgreSQL>("postgres:latest") {
  init { // Needed for M1
    waitingFor(Wait.forListeningPort())
  }
}

/**
 * Configuration of our Kotest Test Project. It contains our Test Container configuration which is
 * used in almost all tests.
 */
object KotestProject : AbstractProjectConfig() {
  private val postgres = PostgreSQL()

  private val dataSource: Env.DataSource by lazy {
    Env.DataSource(postgres.jdbcUrl, postgres.username, postgres.password, postgres.driverClassName)
  }

  val env: Env by lazy { Env().copy(dataSource = dataSource) }

  val dependencies = ProjectResource(resource { dependencies(env) })
  private val hikari = ProjectResource(resource { hikari(env.dataSource) })

  private val resetDatabaseListener =
    object : TestListener {
      override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        super.afterTest(testCase, result)
        hikari.get().connection.use { conn ->
          conn.prepareStatement("TRUNCATE users CASCADE").executeLargeUpdate()
        }
      }
    }

  override fun extensions(): List<Extension> =
    listOf(StartablePerProjectListener(postgres), hikari, dependencies, resetDatabaseListener)
}
