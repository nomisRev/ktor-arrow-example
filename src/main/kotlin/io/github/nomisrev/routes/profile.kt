@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.routes

import arrow.core.raise.ensure
import io.github.nomisrev.MissingParameter
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.env.Env
import io.github.nomisrev.repo.UserPersistence
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable

@Serializable data class ProfileWrapper<T : Any>(val profile: T)

@Serializable
data class Profile(
  val username: String,
  val bio: String,
  val image: String,
  val following: Boolean
)

@Resource("/profiles")
data class ProfilesResource(val parent: RootResource = RootResource) {
  @Resource("/{username?}")
  data class Username(val parent: ProfilesResource = ProfilesResource(), val username: String?)

  @Resource("/{username}/follow")
  data class Follow(val parent: ProfilesResource = ProfilesResource(), val username: String)
}

context(Env.Auth, UserPersistence)
fun Route.profileRoutes() {
  get<ProfilesResource.Username> { route ->
    conduit(HttpStatusCode.OK) {
        ensure(!route.username.isNullOrBlank()) { MissingParameter("username") }
        selectProfile(route.username)
      }
  }

  delete<ProfilesResource.Follow> { follow ->
    jwtAuth { _, userId ->
      conduit(HttpStatusCode.OK) {
          unfollowProfile(follow.username, userId)
          val userUnfollowed = select(follow.username)
          ProfileWrapper(
            Profile(userUnfollowed.username, userUnfollowed.bio, userUnfollowed.image, false)
          )
        }
    }
  }
}
