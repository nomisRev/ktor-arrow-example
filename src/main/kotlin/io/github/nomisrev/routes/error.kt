package io.github.nomisrev.routes

import arrow.core.Either
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.ArticleNotFound
import io.github.nomisrev.ApiError.CannotGenerateSlug
import io.github.nomisrev.ApiError.CommentNotFound
import io.github.nomisrev.ApiError.EmailAlreadyExists
import io.github.nomisrev.ApiError.EmptyUpdate
import io.github.nomisrev.ApiError.IncorrectInput
import io.github.nomisrev.ApiError.JwtGeneration
import io.github.nomisrev.ApiError.PasswordNotMatched
import io.github.nomisrev.ApiError.ProfileNotFound
import io.github.nomisrev.ApiError.Unexpected
import io.github.nomisrev.ApiError.UserFollowingHimself
import io.github.nomisrev.ApiError.UserNotFound
import io.github.nomisrev.ApiError.UserUnfollowingHimself
import io.github.nomisrev.ApiError.UsernameAlreadyExists
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.Serializable

@Serializable data class GenericErrorModel(val errors: GenericErrorModelErrors)

@Serializable data class GenericErrorModelErrors(val body: List<String>)

fun GenericErrorModel(vararg msg: String): GenericErrorModel =
  GenericErrorModel(GenericErrorModelErrors(msg.toList()))

context(PipelineContext<Unit, ApplicationCall>)

suspend inline fun <reified A : Any> Either<ApiError, A>.respond(status: HttpStatusCode): Unit =
  when (this) {
    is Either.Left -> respond(value)
    is Either.Right -> call.respond(status, value)
  }

@Suppress("ComplexMethod")
suspend fun PipelineContext<Unit, ApplicationCall>.respond(error: ApiError): Unit =
  when (error) {
    PasswordNotMatched -> call.respond(HttpStatusCode.Unauthorized)
    is IncorrectInput ->
      unprocessable(
        error.errors.joinToString { field -> "${field.field}: ${field.errors.joinToString()}" }
      )
    is Unexpected ->
      internal(
        """
        Unexpected failure occurred:
          - description: ${error.description}
          - cause: ${error.error}
        """.trimIndent()
      )
    is EmptyUpdate -> unprocessable(error.description)
    is ArticleNotFound -> unprocessable("Article ${error.slug} does not exist")
    is CannotGenerateSlug -> unprocessable(error.description)
    is CommentNotFound -> unprocessable("Comment for id ${error.commentId} not found")
    is EmailAlreadyExists -> unprocessable("${error.email} is already registered")
    is JwtGeneration -> unprocessable(error.description)
    is ProfileNotFound -> unprocessable("Profile for ${error.profile.username} not found")
    is UserFollowingHimself ->
      unprocessable("${error.profile.username} cannot follow ${error.profile.username}")
    is UserNotFound -> unprocessable("User with ${error.property} not found")
    is UserUnfollowingHimself ->
      unprocessable("${error.profile.username} cannot unfollow ${error.profile.username}")
    is UsernameAlreadyExists -> unprocessable("Username ${error.username} already exists")
    is ApiError.JwtInvalid -> unprocessable(error.description)
  }

private suspend inline fun PipelineContext<Unit, ApplicationCall>.unprocessable(
  error: String
): Unit = call.respond(HttpStatusCode.UnprocessableEntity, GenericErrorModel(error))

private suspend inline fun PipelineContext<Unit, ApplicationCall>.internal(error: String): Unit =
  call.respond(HttpStatusCode.InternalServerError, GenericErrorModel(error))
