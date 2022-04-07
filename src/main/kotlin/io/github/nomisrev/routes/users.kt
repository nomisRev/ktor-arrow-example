package io.github.nomisrev.routes

import arrow.core.Either
import arrow.core.computations.either
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.Unexpected
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.service.JwtService
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

@Serializable data class NewUserRequest(val user: NewUser)

@Serializable data class NewUser(val username: String, val email: String, val password: String)

@Serializable data class UserResponse(val user: User)

@Serializable data class UpdateUserRequest(val user: UpdateUser)

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

@Serializable data class LoginUserRequest(val user: LoginUser)

fun Application.userRoutes(
  userService: UserService,
  jwtService: JwtService,
) = routing {
  route("/users") {
    /* Registration: POST /api/users */
    post {
      val res =
        either<ApiError, UserResponse> {
          val (username, email, password) = receiveCatching<NewUserRequest>().bind().user
          val token = userService.register(username, email, password).bind().value
          UserResponse(User(email, token, username, "", ""))
        }
      respond(res, HttpStatusCode.Created)
    }
    post("/login") {
      val res =
        either<ApiError, UserResponse> {
          val (email, password) = receiveCatching<LoginUserRequest>().bind().user
          val (token, info) = userService.login(email, password).bind()
          UserResponse(User(email, token.value, info.username, info.bio, info.image))
        }
      respond(res, HttpStatusCode.OK)
    }
  }

  /* Get Current User: GET /api/user */
  get("/user") {
    jwtAuth(jwtService) { (token, userId) ->
      val res =
        either<ApiError, UserResponse> {
          val info = userService.getUser(userId).bind()
          UserResponse(User(info.email, token.value, info.username, info.bio, info.image))
        }
      respond(res, HttpStatusCode.OK)
    }
  }

  /* Update current user: PUT /api/user */
  put("/user") {
    jwtAuth(jwtService) { (token, userId) ->
      val res =
        either<ApiError, UserResponse> {
          val (email, username, password, bio, image) =
            receiveCatching<UpdateUserRequest>().bind().user
          val info = userService.update(userId, email, username, password, bio, image).bind()
          UserResponse(User(info.email, token.value, info.username, info.bio, info.image))
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
