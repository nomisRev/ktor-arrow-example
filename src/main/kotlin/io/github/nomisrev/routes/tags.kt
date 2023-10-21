@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.routes

import io.github.nomisrev.repo.TagPersistence
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import kotlinx.serialization.Serializable

@Serializable data class TagsResponse(val tags: List<String>)

@Resource("/tags") data class TagsResource(val parent: RootResource = RootResource)

context (Routing, TagPersistence)
fun tagRoutes(): Route =
  get<TagsResource> {
    val tags = selectTags()
    call.respond(TagsResponse(tags))
  }
