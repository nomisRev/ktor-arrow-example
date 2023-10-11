package io.github.nomisrev.routes

import arrow.core.raise.either
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.service.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.resources.delete
import io.ktor.server.routing.Route
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.util.getOrFail
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
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
            val username = parameters(USERNAME, ::GetProfile).bind().username
            profileService.getProfile(username).bind()
        }.respond(HttpStatusCode.OK)
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

@OptIn(ExperimentalSerializationApi::class)
private inline fun <reified A : Any> PipelineContext<Unit, ApplicationCall>.parameters(
    parameters: String,
    noinline toRight: (String) -> A,
): Either<IncorrectJson, A> =
    Either.catchOrThrow<MissingFieldException, A> {
        call.parameters.getOrFail(parameters).let { toRight(it) }
    }.mapLeft(::IncorrectJson)