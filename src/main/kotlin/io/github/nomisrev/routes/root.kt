package io.github.nomisrev.routes

import io.github.nomisrev.env.Dependencies
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.routes(deps: Dependencies) = routing {
  userRoutes(deps.userService, deps.jwtService)
  articleRoutes(deps.articleService, deps.jwtService)
  tagRoutes(deps.tagPersistence)
}

@Resource("/api") data object RootResource
