@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.routes

import io.github.nomisrev.repo.TagPersistence
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable data class TagsResponse(val tags: List<String>)

fun Route.tagRoutes(tagPersistence: TagPersistence) {
  route("/api/tags") {
    /* Registration: GET /api/tags */
    get {
      val tags = tagPersistence.selectTags()
      call.respond(TagsResponse(tags))
    }
  }
}
