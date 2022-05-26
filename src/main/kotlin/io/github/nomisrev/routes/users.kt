package io.github.nomisrev.routes

import arrow.core.Either
import arrow.core.continuations.EffectScope
import arrow.core.continuations.either
import io.github.nomisrev.DomainError
import io.github.nomisrev.Unexpected
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.service.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.Serializable

// Conduit OpenAPI user routes types
@Serializable
data class UserWrapper<T : Any>(val user: T)

@Serializable
data class NewUser(val username: String, val email: String, val password: String)

@Serializable
data class User(
  val email: String,
  val token: String,
  val username: String,
  val bio: String,
  val image: String
)

fun Application.userRoutes(users: UserService) = routing {
  route("/users") {
    /* Registration: POST /api/users */
    post {
      either<DomainError, UserWrapper<User>> {
        val (username, email, password) = receiveCatching<UserWrapper<NewUser>>().user
        val token = users.register(RegisterUser(username, email, password)).bind().value
        UserWrapper(User(email, token, username, "", ""))
      }.respond(HttpStatusCode.Created)
    }
  }
}

context(EffectScope<Unexpected>)
private suspend inline fun <reified A : Any> PipelineContext<Unit, ApplicationCall>.receiveCatching(): A =
  Either.catch { call.receive<A>() }.mapLeft { e ->
    Unexpected(e.message ?: "Received malformed JSON for ${A::class.simpleName}", e)
  }.bind()
