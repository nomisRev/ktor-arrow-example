package io.github.nomisrev.routes

import arrow.core.Either
import io.github.nomisrev.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable data class GenericErrorModel(val errors: GenericErrorModelErrors)

@Serializable data class GenericErrorModelErrors(val body: List<String>)

fun GenericErrorModel(vararg msg: String): GenericErrorModel =
  GenericErrorModel(GenericErrorModelErrors(msg.toList()))

context(PipelineContext<Unit, ApplicationCall>)

suspend inline fun <reified A : Any> Either<DomainError, A>.respond(status: HttpStatusCode): Unit =
  when (this) {
    is Either.Left -> respond(value)
    is Either.Right -> call.respond(status, value)
  }

@OptIn(ExperimentalSerializationApi::class)
@Suppress("ComplexMethod")
suspend fun PipelineContext<Unit, ApplicationCall>.respond(error: DomainError): Unit =
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

private suspend inline fun PipelineContext<Unit, ApplicationCall>.unprocessable(
  error: String
): Unit = call.respond(HttpStatusCode.UnprocessableEntity, GenericErrorModel(error))
