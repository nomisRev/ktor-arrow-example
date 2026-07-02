package io.github.nomisrev

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import com.sksamuel.cohort.Cohort
import io.github.nomisrev.articles.articleRoutes
import io.github.nomisrev.articles.commentRoutes
import io.github.nomisrev.env.Dependencies
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.configure
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.profiles.profileRoutes
import io.github.nomisrev.tags.tagRoutes
import io.github.nomisrev.users.userRoutes
import io.ktor.server.application.*
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.*

fun main() = SuspendApp {
    val env = Env()
    resourceScope {
        val dependencies = dependencies(env)
        server(Netty, host = env.http.host, port = env.http.port) { app(dependencies) }
        awaitCancellation()
    }
}

fun Application.app(module: Dependencies) {
    configure(module.jwtService)
    routing {
        userRoutes(module.userService, module.jwtService)
        tagRoutes(module.tagPersistence)
        articleRoutes(module.articleService, module.jwtService)
        commentRoutes(module.userService, module.articleService, module.jwtService)
        profileRoutes(module.userPersistence, module.jwtService)
    }
    install(Cohort) { healthcheck("/readiness", module.healthCheck) }
}
