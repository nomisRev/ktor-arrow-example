package io.github.nomisrev.routes

import arrow.core.raise.Raise
import arrow.core.raise.either
import io.github.nomisrev.ArticleBySlugNotFound
import io.github.nomisrev.CannotGenerateSlug
import io.github.nomisrev.DomainError
import io.github.nomisrev.EmailAlreadyExists
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.IncorrectJson
import io.github.nomisrev.JwtGeneration
import io.github.nomisrev.JwtInvalid
import io.github.nomisrev.MissingParameter
import io.github.nomisrev.PasswordNotMatched
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.UsernameAlreadyExists
import io.github.nomisrev.KtorCtx
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class GenericErrorModel(val errors: GenericErrorModelErrors)

@Serializable
data class GenericErrorModelErrors(val body: List<String>)

context(KtorCtx)
suspend inline fun <reified A : Any> conduit(
  status: HttpStatusCode,
  crossinline block: suspend context(Raise<DomainError>) () -> A
): Unit = either {
  block(this)
}.fold({ respond(it) }, { call.respond<A>(status, it) })

@OptIn(ExperimentalSerializationApi::class)
@Suppress("ComplexMethod")
suspend fun KtorCtx.respond(error: DomainError): Unit =
  when (error) {
    PasswordNotMatched -> call.respond(HttpStatusCode.Unauthorized)
    is IncorrectInput ->
      unprocessable(error.errors.map { field -> "${field.field}: ${field.errors.joinToString()}" })

    is IncorrectJson ->
      unprocessable("Json is missing fields: ${error.exception.missingFields.joinToString()}")

    is EmptyUpdate -> unprocessable(error.description)
    is EmailAlreadyExists -> unprocessable("${error.email} is already registered")
    is JwtGeneration -> unprocessable(error.description)
    is UserNotFound -> unprocessable("User with ${error.property} not found")
    is UsernameAlreadyExists -> unprocessable("Username ${error.username} already exists")
    is JwtInvalid -> unprocessable(error.description)
    is CannotGenerateSlug -> unprocessable(error.description)
    is ArticleBySlugNotFound -> unprocessable("Article by slug ${error.slug} not found")
    is MissingParameter -> unprocessable("Missing ${error.name} parameter in request")
  }

private suspend inline fun KtorCtx.unprocessable(
  error: String
): Unit =
  call.respond(
    HttpStatusCode.UnprocessableEntity,
    GenericErrorModel(GenericErrorModelErrors(listOf(error)))
  )

private suspend inline fun PipelineContext<Unit, ApplicationCall>.unprocessable(
  errors: List<String>
): Unit =
  call.respond(
    HttpStatusCode.UnprocessableEntity,
    GenericErrorModel(GenericErrorModelErrors(errors))
  )
