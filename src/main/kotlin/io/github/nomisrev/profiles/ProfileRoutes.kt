@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.profiles

import arrow.core.raise.context.ensure
import io.github.nomisrev.Api
import io.github.nomisrev.MissingParameter
import io.github.nomisrev.auth.JwtConfig
import io.github.nomisrev.auth.JwtContext
import io.github.nomisrev.auth.authenticateWith
import io.github.nomisrev.auth.principal
import io.github.nomisrev.route
import io.github.nomisrev.users.UserPersistence
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

fun Route.profileRoutes(userPersistence: UserPersistence, jwtService: JwtConfig<JwtContext>) {
    authenticateWith(jwtService.orAnonymous()) {
        route(Api.Profiles.Username.get) {
            val username = idOf(Api.Profiles.Username)
            ensure(username.isNotBlank()) { MissingParameter("username cannot be null or blank") }
            val profile = userPersistence.selectProfile(username, call.principal?.userId)
            respond(ProfileWrapper(profile))
        }
    }

    authenticateWith(jwtService) {
        route(Api.Profiles.Username.Follow.add) {
            val username = idOf(Api.Profiles.Username)
            userPersistence.followProfile(username, call.principal.userId)
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

        route(Api.Profiles.Username.Follow.remove) {
            val username = idOf(Api.Profiles.Username)
            userPersistence.unfollowProfile(username, call.principal.userId)
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
