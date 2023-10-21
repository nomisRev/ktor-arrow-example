package io.github.nomisrev.routes

import io.github.nomisrev.env.Env
import io.github.nomisrev.repo.TagPersistence
import io.github.nomisrev.repo.UserPersistence
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

context(UserPersistence, TagPersistence, Env.Auth)
fun Application.routes() = routing {
  userRoutes()
  tagRoutes()
}

@Resource("/api")
data object RootResource
