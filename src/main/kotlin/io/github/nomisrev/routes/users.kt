package io.github.nomisrev.routes

import arrow.core.raise.Raise
import arrow.core.raise.catch
import io.github.nomisrev.IncorrectJson
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.env.Env
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.service.Login
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.service.Update
import io.github.nomisrev.service.login
import io.github.nomisrev.service.register
import io.github.nomisrev.service.update
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.Routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable

@Serializable
data class UserWrapper<T : Any>(val user: T)

@Serializable
data class NewUser(val username: String, val email: String, val password: String)

@Serializable
data class UpdateUser(
  val email: String? = null,
  val username: String? = null,
  val password: String? = null,
  val bio: String? = null,
  val image: String? = null
)

@Serializable
data class User(
  val email: String,
  val token: String,
  val username: String,
  val bio: String,
  val image: String
)

@Serializable
data class LoginUser(val email: String, val password: String)

@Resource("/users")
data class UsersResource(val parent: RootResource = RootResource) {
  @Resource("/login")
  data class Login(val parent: UsersResource = UsersResource())
}

@Resource("/user")
data class UserResource(val parent: RootResource = RootResource)

context(Routing, UserPersistence, Env.Auth)
fun userRoutes() {
  /* Registration: POST /api/users */
  post<UsersResource> {
    conduit(HttpStatusCode.Created) {
      val (username, email, password) = receiveCatching<UserWrapper<NewUser>>().user
      val token = register(RegisterUser(username, email, password)).value
      UserWrapper(User(email, token, username, "", ""))
    }
  }
  post<UsersResource.Login> {
    conduit(HttpStatusCode.OK) {
      val (email, password) = receiveCatching<UserWrapper<LoginUser>>().user
      val (token, info) = login(Login(email, password))
      UserWrapper(User(email, token.value, info.username, info.bio, info.image))
    }
  }
  /* Get Current User: GET /api/user */
  get<UserResource> {
    jwtAuth { (token, userId) ->
      conduit(HttpStatusCode.OK) {
        val info = select(userId)
        UserWrapper(User(info.email, token.value, info.username, info.bio, info.image))
      }
    }
  }

  /* Update current user: PUT /api/user */
  put<UserResource> {
    jwtAuth { (token, userId) ->
      conduit(HttpStatusCode.OK) {
        val (email, username, password, bio, image) =
          receiveCatching<UserWrapper<UpdateUser>>().user
        val info = update(Update(userId, username, email, password, bio, image))
        UserWrapper(User(info.email, token.value, info.username, info.bio, info.image))
      }
    }
  }
}

context(Raise<IncorrectJson>)
@OptIn(ExperimentalSerializationApi::class)
private suspend inline fun <reified A : Any> PipelineContext<Unit, ApplicationCall>.receiveCatching(): A =
  catch({ call.receive() }) { e: MissingFieldException -> shift(IncorrectJson(e)) }
