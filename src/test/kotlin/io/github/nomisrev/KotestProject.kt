package io.github.nomisrev

import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.resource
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.dependencies
import io.kotest.assertions.arrow.fx.coroutines.ProjectResource
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
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
    private val postgres = projectResource { autoCloseable { PostgreSQL().also { it.start() } } }

    suspend fun postgres(): PostgreSQLContainer<*> = postgres.get()

    val dependencies = projectResource {
        dependencies(
            Env()
                .copy(
                    dataSource =
                        Env.DataSource(
                            postgres().jdbcUrl,
                            postgres().username,
                            postgres().password,
                            postgres().driverClassName,
                        )
                )
        )
    }

    override val globalAssertSoftly: Boolean = true

    override val extensions: List<Extension>
        get() = listOf(dependencies)
}

private fun <A> projectResource(block: suspend ResourceScope.() -> A) =
    ProjectResource(resource(block))
