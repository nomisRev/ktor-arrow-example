@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.routes

import arrow.core.raise.context.ensure
import io.github.nomisrev.MissingParameter
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.service.JwtService
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import opensavvy.spine.server.respond

@Serializable data class ProfileWrapper<T : Any>(val profile: T)

@Serializable
data class Profile(
    val username: String,
    val bio: String,
    val image: String,
    val following: Boolean,
)

fun Route.profileRoutes(userPersistence: UserPersistence, jwtService: JwtService) {
    route(Api.Profiles.Username.get) {
        val username = idOf(Api.Profiles.Username)
        ensure(username.isNotBlank()) { MissingParameter("username cannot be null or blank") }
        respond(userPersistence.selectProfile(username))
    }

    route(Api.Profiles.Username.Follow.add) {
        jwtAuth(jwtService) { (_, userId) ->
            val username = idOf(Api.Profiles.Username)
            userPersistence.followProfile(username, userId)
            val userFollowed = userPersistence.select(username)
            respond(
                ProfileWrapper(
                    Profile(
                        userFollowed.username,
                        userFollowed.bio,
                        userFollowed.image,
                        true,
                    )
                )
            )
        }
    }

    route(Api.Profiles.Username.Follow.remove) {
        jwtAuth(jwtService) { (_, userId) ->
            val username = idOf(Api.Profiles.Username)
            userPersistence.unfollowProfile(username, userId)
            val userUnfollowed = userPersistence.select(username)
            respond(
                ProfileWrapper(
                    Profile(
                        userUnfollowed.username,
                        userUnfollowed.bio,
                        userUnfollowed.image,
                        false,
                    )
                )
            )
        }
    }
}
