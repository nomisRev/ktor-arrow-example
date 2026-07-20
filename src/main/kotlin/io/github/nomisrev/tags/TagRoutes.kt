@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.tags

import io.github.nomisrev.route
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import opensavvy.spine.server.respond

@Serializable
data class TagsResponse(val tags: List<String>)

fun Route.tagRoutes(tagService: TagService) {
    route(Tags.list) {
        val tags = tagService.selectTags()
        respond(TagsResponse(tags))
    }
}
