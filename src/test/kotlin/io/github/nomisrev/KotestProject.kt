package io.github.nomisrev

import io.github.nomisrev.env.Env
import io.github.nomisrev.env.dependencies
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.testcontainers.StartablePerProjectListener
import org.testcontainers.containers.PostgreSQLContainer

private class PostgreSQL : PostgreSQLContainer<PostgreSQL>("postgres:latest")

/**
 * Configuration of our Kotest Test Project.
 * It contains our Test Container configuration which is used in almost all tests.
 */
object KotestProject : AbstractProjectConfig() {
  private val postgres = StartablePerProjectListener(
    PostgreSQL()
      .withDatabaseName("ktor-arrow-example-database")
      .withUsername("postgres")
      .withPassword("postgres")
      .withExposedPorts(PostgreSQLContainer.POSTGRESQL_PORT),
    "postgres"
  )

  private val dataSource: Env.DataSource by lazy {
    Env.DataSource(
      postgres.startable.jdbcUrl,
      postgres.startable.username,
      postgres.startable.password,
      postgres.startable.driverClassName
    )
  }

  private val env: Env by lazy { Env().copy(dataSource = dataSource) }

  val dependencies = TestResource { dependencies(env) }

  override fun extensions(): List<Extension> =
    listOf(postgres, dependencies)
}
