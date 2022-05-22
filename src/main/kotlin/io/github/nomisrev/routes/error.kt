package io.github.nomisrev.routes

import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.EmailAlreadyExists
import io.github.nomisrev.ApiError.EmptyUpdate
import io.github.nomisrev.ApiError.IncorrectInput
import io.github.nomisrev.ApiError.JwtGeneration
import io.github.nomisrev.ApiError.JwtInvalid
import io.github.nomisrev.ApiError.PasswordNotMatched
import io.github.nomisrev.ApiError.Unexpected
import io.github.nomisrev.ApiError.UserNotFound
import io.github.nomisrev.ApiError.UsernameAlreadyExists
import io.github.nomisrev.KtorCtx
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
private data class GenericErrorModel(val errors: GenericErrorModelErrors) {
  constructor(msg: String): this(GenericErrorModelErrors(listOf(msg)))
}

@Serializable
private data class GenericErrorModelErrors(val body: List<String>)

context(KtorCtx)
suspend inline fun <reified A : Any> conduit(
    status: HttpStatusCode,
    crossinline block: suspend context(EffectScope<ApiError>) () -> A
): Unit = effect<ApiError, A> {
    block(this)
}.fold({ respond(it) }, { call.respond(status, it) })

@Suppress("ComplexMethod")
suspend fun KtorCtx.respond(error: ApiError): Unit =
  when (error) {
    PasswordNotMatched -> call.respond(HttpStatusCode.Unauthorized)
    is IncorrectInput ->
      unprocessable(error.errors.joinToString { field -> "${field.field}: ${field.errors.joinToString()}" })
    is EmptyUpdate -> unprocessable(error.description)
    is UserNotFound -> unprocessable("User with ${error.property} not found")
    is UsernameAlreadyExists -> unprocessable("Username ${error.username} already exists")
    is EmailAlreadyExists -> unprocessable("${error.email} is already registered")
    is JwtGeneration -> unprocessable(error.description)
    is JwtInvalid -> unprocessable(error.description)
    is Unexpected ->
      internal(
        """
        Unexpected failure occurred:
          - description: ${error.description}
          - cause: ${error.error}
        """.trimIndent()
      )
  }

private suspend inline fun KtorCtx.unprocessable(
  error: String
): Unit = call.respond(HttpStatusCode.UnprocessableEntity, GenericErrorModel(error))

private suspend inline fun KtorCtx.internal(error: String): Unit =
  call.respond(HttpStatusCode.InternalServerError, GenericErrorModel(error))
