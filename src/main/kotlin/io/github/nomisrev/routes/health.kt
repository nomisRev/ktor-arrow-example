package io.github.nomisrev.routes

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.server.application.Application
import io.ktor.server.application.install

fun Application.health(healthCheck: HealthCheckRegistry) {
  install(Cohort) { healthcheck("/readiness", healthCheck) }
}
