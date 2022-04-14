package io.github.nomisrev.routes

import arrow.core.Either
import arrow.core.continuations.either
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.Unexpected
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.service.JwtService
import io.github.nomisrev.service.LoginUser
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.service.UpdateUser
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

@Serializable data class UserWrapper<T>(val user: T)

@Serializable data class NewUser(val username: String, val email: String, val password: String)

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

@Serializable data class LoginUser(val email: String, val password: String)

fun Application.userRoutes(
  userService: UserService,
  jwtService: JwtService,
) = routing {
  route("/users") {
    /* Registration: POST /api/users */
    post {
      val res =
        either<ApiError, UserWrapper<User>> {
          val (username, email, password) = receiveCatching<UserWrapper<NewUser>>().bind().user
          val token = userService.register(RegisterUser(username, email, password)).bind().value
          UserWrapper(User(email, token, username, "", ""))
        }
      respond(res, HttpStatusCode.Created)
    }
    post("/login") {
      val res =
        either<ApiError, UserWrapper<User>> {
          val (email, password) = receiveCatching<UserWrapper<LoginUser>>().bind().user
          val (token, info) = userService.login(LoginUser(email, password)).bind()
          UserWrapper(User(email, token.value, info.username, info.bio, info.image))
        }
      respond(res, HttpStatusCode.OK)
    }
  }

  /* Get Current User: GET /api/user */
  get("/user") {
    jwtAuth(jwtService) { (token, userId) ->
      val res =
        either<ApiError, UserWrapper<User>> {
          val info = userService.getUser(userId).bind()
          UserWrapper(User(info.email, token.value, info.username, info.bio, info.image))
        }
      respond(res, HttpStatusCode.OK)
    }
  }

  /* Update current user: PUT /api/user */
  put("/user") {
    jwtAuth(jwtService) { (token, userId) ->
      val res =
        either<ApiError, UserWrapper<User>> {
          val (_, username, email, password, bio, image) =
            receiveCatching<UserWrapper<UpdateUser>>().bind().user
          val info =
            userService.update(UpdateUser(userId, username, email, password, bio, image)).bind()
          UserWrapper(User(info.email, token.value, info.username, info.bio, info.image))
        }
      respond(res, HttpStatusCode.OK)
    }
  }
}

// TODO improve how we receive models with validation
private suspend inline fun <reified A : Any> PipelineContext<
  Unit, ApplicationCall>.receiveCatching(): Either<ApiError, A> =
  Either.catch { call.receive<A>() }.mapLeft { e ->
    Unexpected(e.message ?: "Received malformed JSON for ${A::class.simpleName}", e)
  }
