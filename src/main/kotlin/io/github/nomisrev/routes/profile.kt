package io.github.nomisrev.routes

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.github.nomisrev.DomainError
import io.github.nomisrev.MissingParameter
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.service.JwtService
import io.github.nomisrev.IncorrectJson
import io.github.nomisrev.repo.UserPersistence
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.resources.delete
import io.ktor.server.routing.Route
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.Serializable

@Serializable
data class ProfileWrapper<T : Any>(val profile: T)

@Serializable
data class Profile(
    val username: String,
    val bio: String,
    val image: String,
    val following: Boolean
)

@Resource("/profiles")
data class ProfilesResource(val parent: RootResource = RootResource) {
    @Resource("/{username}/follow")
    data class Follow(val parent: ProfilesResource = ProfilesResource(), val username: String)

    @Resource("/{$USERNAME}")
    data class Username(val parent: ProfileResource = ProfileResource(), val username: String)
}

fun Route.profileRoutes(
    userPersistence: UserPersistence,
    jwtService: JwtService
) {
    get<ProfileResource.Username> {
        either {
            val username = parameter(USERNAME, ::GetProfile).bind().username
            repo.selectProfile(username).bind()
        }
            .respond(HttpStatusCode.OK)
    }
    delete<ProfilesResource.Follow> { follow ->
        jwtAuth(jwtService) { (_, userId) ->
            either {
                userPersistence.unfollowProfile(follow.username, userId)
                val userUnfollowed = userPersistence.select(follow.username).bind()
                ProfileWrapper(Profile(userUnfollowed.username, userUnfollowed.bio, userUnfollowed.image, false))
            }.respond(HttpStatusCode.OK)
        }
    }
}

private inline fun <reified A : Any> PipelineContext<Unit, ApplicationCall>.parameter(
    parameter: String,
    noinline toRight: (String) -> A,
): Either<DomainError, A> =
    call.parameters[parameter]?.let(toRight)?.right() ?: MissingParameter(parameter).left()

