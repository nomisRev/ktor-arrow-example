package io.github.nomisrev.routes

import arrow.core.Either
import arrow.core.continuations.EffectScope
import arrow.core.continuations.either
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.Unexpected
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.service.JwtService
import io.github.nomisrev.service.Login
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.service.Update
import io.github.nomisrev.service.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
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

fun Application.userRoutes(
  userService: UserService,
  jwtService: JwtService,
) = routing {
  route("/users") {
    /* Registration: POST /api/users */
    post {
      either<ApiError, UserWrapper<User>> {
        val (username, email, password) = receiveCatching<UserWrapper<NewUser>>().user
        val token = userService.register(RegisterUser(username, email, password)).value
        UserWrapper(User(email, token, username, "", ""))
      }.respond(HttpStatusCode.Created)
    }
    post("/login") {
      either<ApiError, UserWrapper<User>> {
        val (email, password) = receiveCatching<UserWrapper<LoginUser>>().user
        val (token, info) = userService.login(Login(email, password))
        UserWrapper(User(email, token.value, info.username, info.bio, info.image))
      }.respond(HttpStatusCode.OK)
    }
  }

  /* Get Current User: GET /api/user */
  get("/user") {
    jwtAuth(jwtService) { (token, userId) ->
      either<ApiError, UserWrapper<User>> {
        val info = userService.getUser(userId)
        UserWrapper(User(info.email, token.value, info.username, info.bio, info.image))
      }.respond(HttpStatusCode.OK)
    }
  }

  /* Update current user: PUT /api/user */
  put("/user") {
    jwtAuth(jwtService) { (token, userId) ->
      either<ApiError, UserWrapper<User>> {
        val (email, username, password, bio, image) =
          receiveCatching<UserWrapper<UpdateUser>>().user
        val info =
          userService.update(Update(userId, username, email, password, bio, image))
        UserWrapper(User(info.email, token.value, info.username, info.bio, info.image))
      }.respond(HttpStatusCode.OK)
    }
  }
}

// TODO improve how we receive models with validation
context(EffectScope<ApiError>)
  private suspend inline fun <reified A : Any> PipelineContext<
  Unit, ApplicationCall>.receiveCatching(): A =
  Either.catch { call.receive<A>() }.mapLeft { e ->
    Unexpected(e.message ?: "Received malformed JSON for ${A::class.simpleName}", e)
  }.bind()
