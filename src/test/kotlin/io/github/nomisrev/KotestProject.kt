package io.github.nomisrev

import io.github.nomisrev.env.Env
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.testcontainers.StartablePerProjectListener
import org.testcontainers.containers.PostgreSQLContainer

object KotestProject : AbstractProjectConfig() {
  private val postgresContainer =
    StartablePerProjectListener(PostgreSQLContainer<Nothing>("postgres:14.1-alpine"), "postgres")
  private val postgres: PostgreSQLContainer<Nothing> = postgresContainer.startable
  private val dataSource: Env.DataSource
    get() = Env.DataSource(
      postgres.jdbcUrl,
      postgres.username,
      postgres.password,
      postgres.driverClassName
    )
  val env: Env
    get() = Env().copy(dataSource = dataSource)

  override fun extensions(): List<Extension> =
    listOf(postgresContainer)
}
