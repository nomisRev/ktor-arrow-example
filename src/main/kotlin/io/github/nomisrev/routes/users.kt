package io.github.nomisrev.routes

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.computations.ensureNotNull
import io.github.nomisrev.service.UserService
import io.github.nomisrev.userIdOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.Serializable

@Serializable data class NewUserRequest(val user: NewUser)

@Serializable data class NewUser(val username: String, val email: String, val password: String)

@Serializable
data class User(
  val email: String,
  val token: String,
  val username: String,
  val bio: String = "",
  val image: String = ""
)

data class UserInfo(val email: String, val username: String, val bio: String, val image: String)

@Serializable data class UserResponse(val user: User)

fun Application.userRoutes(userService: UserService) = routing {
  route("/users") {
    /* Registration: POST /api/users */
    post {
      val res =
        either<GenericErrorModel, UserResponse> {
          val user =
            Either.catch { call.receive<NewUserRequest>() }
              .mapLeft { GenericErrorModel(it.message ?: "Received malformed JSON for NewUser") }
              .bind()
              .user
          val userId = userService.register(user).toGenericError().bind()
          val token = userService.generateJwtToken(userId, user.password).toGenericError().bind()
          UserResponse(User(user.email, token, user.username))
        }
      when (res) {
        is Either.Left -> call.respond(HttpStatusCode.UnprocessableEntity, res.value)
        is Either.Right -> call.respond(HttpStatusCode.Created, res.value)
      }
    }
  }

  authenticate {
    /* Get Current User: GET /api/user */
    get("/user") {
      val res =
        either<GenericErrorModel, UserResponse> {
          val userId =
            ensureNotNull(call.userIdOrNull()) { GenericErrorModel("userId not present in JWT") }
          val user = userService.getUser(userId).toGenericError().bind()
          ensureNotNull(user) { GenericErrorModel("User not found") }
          UserResponse(User(user.email, "", user.username, user.bio, user.image))
        }
      when (res) {
        is Either.Left -> call.respond(HttpStatusCode.UnprocessableEntity, res.value)
        is Either.Right -> call.respond(HttpStatusCode.OK, res.value)
      }
    }
  }
}

fun <A> Either<UserService.Error, A>.toGenericError(): Either<GenericErrorModel, A> =
  mapLeft(UserService.Error::toGenericErrorModel)

// context(EitherEffect<GenericErrorModel, *>)
// suspend fun <A> Either<UserServiceError, A>.bind(): A =
//    mapLeft(UserServiceError::toGenericErrorModel).bind()

// New Context receivers :party:
context(PipelineContext<Unit, ApplicationCall>)

suspend inline fun <reified A : Any> Either<GenericErrorModel, A>.respond(
  status: HttpStatusCode
): Unit =
  when (this@respond) {
    is Either.Left -> call.respond(HttpStatusCode.UnprocessableEntity, value)
    is Either.Right -> call.respond(status, value)
  }
