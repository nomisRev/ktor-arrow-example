package io.github.nomisrev.routes

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.Serializable

sealed interface ApiError

@Serializable data class GenericErrorModel(val errors: GenericErrorModelErrors) : ApiError

@Serializable data class GenericErrorModelErrors(val body: List<String>)

object Unauthorized : ApiError

fun GenericErrorModel(vararg msg: String): GenericErrorModel =
  GenericErrorModel(GenericErrorModelErrors(msg.toList()))

suspend inline fun <reified A : Any> PipelineContext<Unit, ApplicationCall>.respond(
  either: Either<ApiError, A>,
  status: HttpStatusCode
): Unit =
  when (either) {
    is Either.Left ->
      when (either.value) {
        is Unauthorized -> call.respond(HttpStatusCode.Unauthorized)
        else -> call.respond(HttpStatusCode.UnprocessableEntity, either.value)
      }
    is Either.Right -> call.respond(status, either.value)
  }

// Playing with Context Receivers

// Fails to compile
// context(EitherEffect<ApiError, *>)
// suspend fun <A> Either<UserService.Error, A>.bind(): A =
//    toGenericError().bind()

// Fails at runtime :(
context(PipelineContext<Unit, ApplicationCall>)

@JvmName("respondContextReceiver")
suspend inline fun <reified A : Any> Either<ApiError, A>.respond(status: HttpStatusCode): Unit =
  respond(this@respond, status)
