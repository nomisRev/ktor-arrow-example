package io.github.nomisrev.routes

import arrow.core.Either
import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.recover
import io.github.nomisrev.ArticleBySlugNotFound
import io.github.nomisrev.CannotGenerateSlug
import io.github.nomisrev.CommentNotFound
import io.github.nomisrev.DomainError
import io.github.nomisrev.EmailAlreadyExists
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.IncorrectJson
import io.github.nomisrev.JwtGeneration
import io.github.nomisrev.JwtInvalid
import io.github.nomisrev.MissingParameter
import io.github.nomisrev.NotArticleAuthor
import io.github.nomisrev.NotCommentAuthor
import io.github.nomisrev.PasswordNotMatched
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.UsernameAlreadyExists
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.utils.io.KtorDsl
import kotlinx.serialization.Serializable
import opensavvy.spine.api.Endpoint
import opensavvy.spine.api.FailureSpec.ByCode
import opensavvy.spine.api.FailureSpec.Never
import opensavvy.spine.api.FailureSpec.Or
import opensavvy.spine.api.Parameters
import opensavvy.spine.server.TypedResponseScope
import opensavvy.spine.server.fail
import opensavvy.spine.server.route

@Serializable data class GenericErrorModel(val errors: GenericErrorModelErrors)

@Serializable data class GenericErrorModelErrors(val body: List<String>)

context(ctx: RoutingContext)
suspend inline fun <E : DomainError, reified A : Any> Either<E, A>.respond(
    status: HttpStatusCode
): Unit =
    when (this) {
        is Either.Left -> ctx.respond(value)
        is Either.Right -> ctx.call.respond(status, value)
    }

@Suppress("DEPRECATION_ERROR")
@KtorDsl
inline fun <
    reified In : Any,
    reified Out : Any,
    reified Failure : Or<Never, ByCode<GenericErrorModel>>,
    reified Params : Parameters,
> Route.route(
    endpoint: Endpoint<In, Out, Failure, Params>,
    crossinline block:
        suspend context(arrow.core.raise.Raise<DomainError>) TypedResponseScope<
            In,
            Out,
            Or<Never, ByCode<GenericErrorModel>>,
            Params,
        >.() -> Unit,
): Unit =
    route(endpoint) response@{
        recover(
            block = { block() },
            recover = { error: DomainError -> fail(error.toGenericErrorModel()) },
        )
    }

fun DomainError.toGenericErrorModel(): GenericErrorModel =
    when (this) {
        PasswordNotMatched ->
            GenericErrorModel(GenericErrorModelErrors(listOf("Password not matched")))

        is IncorrectInput ->
            GenericErrorModel(
                GenericErrorModelErrors(
                    this.errors.map { field -> "${field.field}: ${field.errors.joinToString()}" }
                )
            )

        is IncorrectJson ->
            GenericErrorModel(
                GenericErrorModelErrors(
                    listOf("Json is missing fields: ${this.exception.missingFields.joinToString()}")
                )
            )

        is EmptyUpdate -> GenericErrorModel(GenericErrorModelErrors(listOf(this.description)))
        is EmailAlreadyExists ->
            GenericErrorModel(
                GenericErrorModelErrors(listOf("${this.email} is already registered"))
            )

        is JwtGeneration -> GenericErrorModel(GenericErrorModelErrors(listOf(this.description)))
        is UserNotFound ->
            GenericErrorModel(
                GenericErrorModelErrors(listOf("User with ${this.property} not found"))
            )

        is UsernameAlreadyExists ->
            GenericErrorModel(
                GenericErrorModelErrors(listOf("Username ${this.username} already exists"))
            )

        is JwtInvalid -> GenericErrorModel(GenericErrorModelErrors(listOf(this.description)))
        is CannotGenerateSlug ->
            GenericErrorModel(GenericErrorModelErrors(listOf(this.description)))

        is ArticleBySlugNotFound ->
            GenericErrorModel(
                GenericErrorModelErrors(listOf("Article by slug ${this.slug} not found"))
            )

        is MissingParameter ->
            GenericErrorModel(
                GenericErrorModelErrors(listOf("Missing ${this.name} parameter in request"))
            )

        is NotArticleAuthor ->
            GenericErrorModel(
                GenericErrorModelErrors(listOf("User is not the author of the article"))
            )

        is CommentNotFound ->
            GenericErrorModel(
                GenericErrorModelErrors(listOf("Comment with ID ${this.commentId} not found"))
            )

        is NotCommentAuthor ->
            GenericErrorModel(
                GenericErrorModelErrors(listOf("User is not the author of the comment"))
            )
    }

@Suppress("ComplexMethod")
suspend fun RoutingContext.respond(error: DomainError): Unit =
    when (error) {
        PasswordNotMatched -> call.respond(HttpStatusCode.Unauthorized)
        else -> call.respond(HttpStatusCode.UnprocessableEntity, error.toGenericErrorModel())
    }
