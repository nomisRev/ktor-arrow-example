@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.tags

import io.github.nomisrev.Api
import io.github.nomisrev.route
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
