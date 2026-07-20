@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.profiles

import arrow.core.nonEmptyListOf
import arrow.core.raise.context.Raise
import arrow.core.raise.context.withError
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.auth.JwtConfig
import io.github.nomisrev.auth.JwtContext
import io.github.nomisrev.auth.authenticateWith
import io.github.nomisrev.auth.principal
import io.github.nomisrev.route
import io.github.nomisrev.users.UserPersistence
import io.github.nomisrev.users.Username
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import opensavvy.spine.server.respond

@Serializable
data class ProfileWrapper<T : Any>(val profile: T)

@Serializable
data class Profile(
    val username: String,
    val bio: String,
    val image: String,
    val following: Boolean,
)

fun Route.profileRoutes(userPersistence: UserPersistence, jwtService: JwtConfig<JwtContext>) {
    authenticateWith(jwtService.orAnonymous()) {
        route(Profiles.Username.get) {
            val username = username(idOf(Profiles.Username))
            val profile = userPersistence.selectProfile(username, call.principal?.userId)
            respond(ProfileWrapper(profile))
        }
    }

    authenticateWith(jwtService) {
        route(Profiles.Username.Follow.add) {
            val username = username(idOf(Profiles.Username))
            val _ = userPersistence.followProfile(username, call.principal.userId)
            val userFollowed = userPersistence.select(username)
            respond(ProfileWrapper(Profile(
                userFollowed.username.value,
                userFollowed.bio,
                userFollowed.image,
                true,
            )))
        }

        route(Profiles.Username.Follow.remove) {
            val username = username(idOf(Profiles.Username))
            userPersistence.unfollowProfile(username, call.principal.userId)
            val userUnfollowed = userPersistence.select(username)
            respond(ProfileWrapper(Profile(
                userUnfollowed.username.value,
                userUnfollowed.bio,
                userUnfollowed.image,
                false,
            )))
        }
    }
}

context(_: Raise<IncorrectInput>)
private fun username(value: String): Username =
    withError({ e -> IncorrectInput(nonEmptyListOf(e)) }) { Username(value) }
