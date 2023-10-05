@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.routes

import io.github.nomisrev.repo.TagPersistence
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable data class TagsResponse(val tags: List<String>)

fun Application.tagRoutes(tagPersistence: TagPersistence) = routing {
  route("/tags") {
    /* Registration: GET /api/tags */
    get {
      val tags = tagPersistence.selectTags()
      call.respond(TagsResponse(tags))
    }
  }
}
