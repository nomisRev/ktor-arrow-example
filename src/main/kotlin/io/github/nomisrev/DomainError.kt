package io.github.nomisrev

import arrow.core.NonEmptyList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException

sealed interface DomainError

sealed interface ValidationError : DomainError

@OptIn(ExperimentalSerializationApi::class)
data class IncorrectJson(val exception: MissingFieldException) : ValidationError

data class EmptyUpdate(val description: String) : ValidationError

data class IncorrectInput(val errors: NonEmptyList<InvalidField>) : ValidationError

data class MissingParameter(val name: String) : ValidationError

sealed interface UserError : DomainError

data class UserNotFound(val property: String) : UserError

data class EmailAlreadyExists(val email: String) : UserError

data class UsernameAlreadyExists(val username: String) : UserError

data object PasswordNotMatched : UserError

sealed interface JwtError : DomainError

data class JwtGeneration(val description: String) : JwtError

data class JwtInvalid(val description: String) : JwtError

sealed interface ArticleError : DomainError

data class CannotGenerateSlug(val description: String) : ArticleError

data class ArticleBySlugNotFound(val slug: String) : ArticleError

data class NotArticleAuthor(val userId: Long, val slug: String) : ArticleError

data class CommentNotFound(val commentId: Long) : ArticleError

data class NotCommentAuthor(val userId: Long, val commentId: Long) : ArticleError

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
            @OptIn(ExperimentalSerializationApi::class)
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
