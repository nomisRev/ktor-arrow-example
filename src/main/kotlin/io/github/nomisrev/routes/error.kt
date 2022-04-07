package io.github.nomisrev.routes

import arrow.core.Either
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.ArticleNotFound
import io.github.nomisrev.ApiError.CannotGenerateSlug
import io.github.nomisrev.ApiError.CommentNotFound
import io.github.nomisrev.ApiError.EmailAlreadyExists
import io.github.nomisrev.ApiError.EmptyUpdate
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

suspend inline fun <reified A : Any> PipelineContext<Unit, ApplicationCall>.respond(
  either: Either<ApiError, A>,
  status: HttpStatusCode
): Unit =
  when (either) {
    is Either.Left -> respond(either.value)
    is Either.Right -> call.respond(status, either.value)
  }

@Suppress("ComplexMethod")
suspend fun PipelineContext<Unit, ApplicationCall>.respond(error: ApiError): Unit =
  when (error) {
    PasswordNotMatched -> call.respond(HttpStatusCode.Unauthorized)
    is Unexpected ->
      call.respond(
        HttpStatusCode.InternalServerError,
        GenericErrorModel(
          """
                        Unexpected failure occurred:
                          - description: ${error.description}
                          - cause: ${error.error}
                    """.trimIndent()
        )
      )
    is EmptyUpdate -> respondUnprocessable(GenericErrorModel(error.description))
    is ArticleNotFound ->
      respondUnprocessable(GenericErrorModel("Article ${error.slug} does not exist"))
    is CannotGenerateSlug -> respondUnprocessable(GenericErrorModel(error.description))
    is CommentNotFound ->
      respondUnprocessable(GenericErrorModel("Comment for id ${error.commentId} not found"))
    is EmailAlreadyExists ->
      respondUnprocessable(GenericErrorModel("${error.email} is already registered"))
    is JwtGeneration -> respondUnprocessable(GenericErrorModel(error.description))
    is ProfileNotFound ->
      respondUnprocessable(GenericErrorModel("Profile for ${error.profile.username} not found"))
    is UserFollowingHimself ->
      respondUnprocessable(
        GenericErrorModel("${error.profile.username} cannot follow ${error.profile.username}")
      )
    is UserNotFound ->
      respondUnprocessable(GenericErrorModel("User with ${error.property} not found"))
    is UserUnfollowingHimself ->
      respondUnprocessable(
        GenericErrorModel("${error.profile.username} cannot unfollow ${error.profile.username}")
      )
    is UsernameAlreadyExists ->
      respondUnprocessable(GenericErrorModel("Username ${error.username} already exists"))
    is ApiError.JwtInvalid -> respondUnprocessable(GenericErrorModel(error.description))
  }

suspend inline fun PipelineContext<Unit, ApplicationCall>.respondUnprocessable(
  error: GenericErrorModel
): Unit = call.respond(HttpStatusCode.UnprocessableEntity, error)

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
