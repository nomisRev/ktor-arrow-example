package io.github.nomisrev.routes

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.ktor.Cohort
import io.ktor.server.application.Application
import io.ktor.server.application.install

fun Application.health(healthCheck: HealthCheckRegistry) {
  install(Cohort) { healthcheck("/readiness", healthCheck) }
}
