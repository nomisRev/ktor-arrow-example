package io.github.nomisrev

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisrev.env.Env
import io.ktor.server.application.Application
import io.ktor.server.application.install

fun Application.cohort(config: Env.DataSource) {
    val hikari = HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.url
        username = config.username
        password = config.password
        driverClassName = config.driver
    })
    val hikariHealth = HealthCheckRegistry {
        register(HikariConnectionsHealthCheck(hikari, minConnections = 1))
    }
    install(Cohort) {
        healthcheck("/readiness", hikariHealth)
        jvmInfo = true
        memory = true
        gc = true
    }
}



























