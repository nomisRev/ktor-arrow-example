@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.routes

import io.github.nomisrev.repo.TagPersistence
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import opensavvy.spine.server.respond

@Serializable data class TagsResponse(val tags: List<String>)

fun Route.tagRoutes(tagPersistence: TagPersistence) {
    route(Api.Tags.list) {
        val tags = tagPersistence.selectTags()
        respond(TagsResponse(tags))
    }
}
