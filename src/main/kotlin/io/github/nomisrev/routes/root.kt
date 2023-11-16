package io.github.nomisrev.routes

import io.github.nomisrev.env.Env
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.FavouritePersistence
import io.github.nomisrev.repo.TagPersistence
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.service.SlugGenerator
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

context(
  Env.Auth,
  SlugGenerator,
  ArticlePersistence,
  UserPersistence,
  TagPersistence,
  FavouritePersistence
)
fun Application.routes() = routing {
  userRoutes()
  tagRoutes()
  articleRoutes()
  profileRoutes()
}

@Resource("/api")
data object RootResource
