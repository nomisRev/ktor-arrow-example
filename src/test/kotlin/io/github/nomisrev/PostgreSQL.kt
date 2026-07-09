package io.github.nomisrev

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer

class PostgreSQL private constructor() : PostgreSQLContainer<PostgreSQL>("postgres:18.4-alpine") {
    companion object {
        val container by lazy { PostgreSQL().also { it.start() } }
        val dataSource by lazy {
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = container.jdbcUrl
                    username = container.username
                    password = container.password
                    driverClassName = container.driverClassName
                }
            )
        }
    }
}
