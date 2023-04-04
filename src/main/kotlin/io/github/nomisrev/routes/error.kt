package io.github.nomisrev.routes

import arrow.core.raise.Raise
import arrow.core.raise.effect
import arrow.core.raise.fold
import io.github.nomisrev.CannotGenerateSlug
import io.github.nomisrev.DomainError
import io.github.nomisrev.EmailAlreadyExists
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.IncorrectJson
import io.github.nomisrev.JwtGeneration
import io.github.nomisrev.JwtInvalid
import io.github.nomisrev.PasswordNotMatched
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.UsernameAlreadyExists
import io.github.nomisrev.KtorCtx
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable data class GenericErrorModel(val errors: GenericErrorModelErrors)

@Serializable data class GenericErrorModelErrors(val body: List<String>)

fun GenericErrorModel(vararg msg: String): GenericErrorModel =
  GenericErrorModel(GenericErrorModelErrors(msg.toList()))

context(KtorCtx)
suspend inline fun <reified A : Any> conduit(
    status: HttpStatusCode,
    crossinline block: suspend context(Raise<DomainError>) () -> A
): Unit = effect {
    block(this)
}.fold({ respond(it) }, { call.respond(status, it) })

@OptIn(ExperimentalSerializationApi::class)
@Suppress("ComplexMethod")
suspend fun KtorCtx.respond(error: DomainError): Unit =
  when (error) {
    PasswordNotMatched -> call.respond(HttpStatusCode.Unauthorized)
    is IncorrectInput ->
      unprocessable(
        error.errors.joinToString { field -> "${field.field}: ${field.errors.joinToString()}" }
      )
    is IncorrectJson ->
      unprocessable("Json is missing fields: ${error.exception.missingFields.joinToString()}")
    is EmptyUpdate -> unprocessable(error.description)
    is EmailAlreadyExists -> unprocessable("${error.email} is already registered")
    is JwtGeneration -> unprocessable(error.description)
    is UserNotFound -> unprocessable("User with ${error.property} not found")
    is UsernameAlreadyExists -> unprocessable("Username ${error.username} already exists")
    is JwtInvalid -> unprocessable(error.description)
    is CannotGenerateSlug -> unprocessable(error.description)
  }

private suspend inline fun KtorCtx.unprocessable(
  error: String
): Unit = call.respond(HttpStatusCode.UnprocessableEntity, GenericErrorModel(error))

suspend inline fun KtorCtx.internal(error: String): Unit =
  call.respond(HttpStatusCode.InternalServerError, GenericErrorModel(error))
