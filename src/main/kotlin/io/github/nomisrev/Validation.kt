@file:OptIn(ExperimentalRaiseAccumulateApi::class)
@file:Suppress("TooManyFunctions")

package io.github.nomisrev

import arrow.core.NonEmptyList
import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.context.Raise
import arrow.core.raise.context.RaiseAccumulate
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.accumulating
import arrow.core.raise.context.ensureOrAccumulate
import arrow.core.raise.context.mapOrAccumulate
import arrow.core.raise.context.withError
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.routes.ArticleResource
import io.github.nomisrev.routes.ArticlesResource
import io.github.nomisrev.routes.FeedLimit
import io.github.nomisrev.routes.FeedOffset
import io.github.nomisrev.routes.NewArticle
import io.github.nomisrev.routes.NewComment
import io.github.nomisrev.service.GetArticles
import io.github.nomisrev.service.GetFeed
import io.github.nomisrev.service.Login
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.service.Update

sealed interface InvalidField {
    val errors: NonEmptyList<String>
    val field: String
}

data class InvalidEmail(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "email"
}

data class InvalidPassword(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "password"
}

data class InvalidTag(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "tag"
}

data class InvalidUsername(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "username"
}

data class InvalidTitle(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "title"
}

data class InvalidDescription(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "description"
}

data class InvalidBody(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "body"
}

data class InvalidEmailOrPassword(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "email or password"
}

context(_: Raise<IncorrectInput>)
fun Login.validate(): Login = withError(::incorrectInput) { validated() }

context(_: Raise<NonEmptyList<InvalidField>>)
private fun Login.validated(): Login = accumulate {
    val email by accumulating { email.validEmail() }
    val password by accumulating { password.validPassword() }
    Login(email, password)
}

context(_: Raise<IncorrectInput>)
fun RegisterUser.validate(): RegisterUser = withError(::incorrectInput) { validated() }

context(_: Raise<NonEmptyList<InvalidField>>)
private fun RegisterUser.validated(): RegisterUser = accumulate {
    val username by accumulating { username.validUsername() }
    val email by accumulating { email.validEmail() }
    val password by accumulating { password.validPassword() }
    RegisterUser(username, email, password)
}

context(_: Raise<IncorrectInput>)
fun Update.validate(): Update = withError(::incorrectInput) { validated() }

context(_: Raise<NonEmptyList<InvalidField>>)
private fun Update.validated(): Update = accumulate {
    val username by accumulating { username?.validUsername() }
    val email by accumulating { email?.validEmail() }
    val password by accumulating { password?.validPassword() }
    Update(userId, username, email, password, bio, image)
}

private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 100
private const val MAX_EMAIL_LENGTH = 350
private const val MIN_USERNAME_LENGTH = 1
private const val MAX_USERNAME_LENGTH = 25

context(_: Raise<InvalidField>)
private fun String.validPassword(): String = passwordValidation()

context(_: Raise<InvalidField>)
private fun String.passwordValidation(): String = withError(::invalidPassword) { passwordRules() }

context(_: Raise<NonEmptyList<String>>)
private fun String.passwordRules(): String = accumulate {
    notBlank()
    minSize(MIN_PASSWORD_LENGTH)
    maxSize(MAX_PASSWORD_LENGTH)
    this@passwordRules
}

context(_: Raise<InvalidField>)
private fun String.validEmail(): String = emailValidation()

context(_: Raise<InvalidField>)
private fun String.emailValidation(): String = withError(::invalidEmail) { trim().emailRules() }

context(_: Raise<NonEmptyList<String>>)
private fun String.emailRules(): String = accumulate {
    notBlank()
    maxSize(MAX_EMAIL_LENGTH)
    looksLikeEmail()
    this@emailRules
}

context(_: Raise<InvalidField>)
private fun String.validUsername(): String = usernameValidation()

context(_: Raise<InvalidField>)
private fun String.usernameValidation(): String =
    withError(::invalidUsername) { trim().usernameRules() }

context(_: Raise<NonEmptyList<String>>)
private fun String.usernameRules(): String = accumulate {
    notBlank()
    minSize(MIN_USERNAME_LENGTH)
    maxSize(MAX_USERNAME_LENGTH)
    this@usernameRules
}

context(_: Raise<InvalidField>)
private fun String.validTitle(): String = withError(::invalidTitle) { trim().notBlankRule() }

context(_: Raise<InvalidField>)
private fun String.validDescription(): String =
    withError(::invalidDescription) { trim().notBlankRule() }

context(_: Raise<InvalidField>)
private fun String.validBody(): String = withError(::invalidBody) { trim().notBlankRule() }

context(_: Raise<NonEmptyList<String>>)
private fun String.notBlankRule(): String = accumulate {
    notBlank()
    this@notBlankRule
}

context(_: Raise<InvalidField>)
private fun List<String>.validTags(): Set<String> =
    withError(::invalidTag) { mapOrAccumulate { it.trim().notBlank() }.toSet() }

private fun incorrectInput(errors: NonEmptyList<InvalidField>): IncorrectInput =
    IncorrectInput(errors)

private fun invalidEmail(errors: NonEmptyList<String>): InvalidField = InvalidEmail(errors)

private fun invalidPassword(errors: NonEmptyList<String>): InvalidField = InvalidPassword(errors)

private fun invalidTag(errors: NonEmptyList<String>): InvalidField = InvalidTag(errors)

private fun invalidUsername(errors: NonEmptyList<String>): InvalidField = InvalidUsername(errors)

private fun invalidTitle(errors: NonEmptyList<String>): InvalidField = InvalidTitle(errors)

private fun invalidDescription(errors: NonEmptyList<String>): InvalidField =
    InvalidDescription(errors)

private fun invalidBody(errors: NonEmptyList<String>): InvalidField = InvalidBody(errors)

private fun invalidFeedOffset(error: InvalidFeedOffset): InvalidField = error

private fun invalidFeedLimit(error: InvalidFeedLimit): InvalidField = error

context(_: RaiseAccumulate<String>)
private fun String.notBlank(): String {
    ensureOrAccumulate(isNotBlank()) { "Cannot be blank" }
    return this
}

context(_: RaiseAccumulate<String>)
private fun String.minSize(size: Int): String {
    ensureOrAccumulate(length >= size) { "is too short (minimum is $size characters)" }
    return this
}

context(_: RaiseAccumulate<String>)
private fun String.maxSize(size: Int): String {
    ensureOrAccumulate(length <= size) { "is too long (maximum is $size characters)" }
    return this
}

private val emailPattern = ".+@.+\\..+".toRegex()

context(_: RaiseAccumulate<String>)
private fun String.looksLikeEmail(): String {
    ensureOrAccumulate(emailPattern.matches(this)) { "'$this' is invalid email" }
    return this
}

context(_: Raise<IncorrectInput>)
fun NewArticle.validate(): NewArticle = withError(::incorrectInput) { validated() }

context(_: Raise<NonEmptyList<InvalidField>>)
private fun NewArticle.validated(): NewArticle = accumulate {
    val title by accumulating { title.validTitle() }
    val description by accumulating { description.validDescription() }
    val body by accumulating { body.validBody() }
    val tagList by accumulating { tagList.validTags().toList() }
    NewArticle(title, description, body, tagList)
}

context(_: Raise<IncorrectInput>)
fun NewComment.validate(): NewComment = withError(::incorrectInput) { validated() }

context(_: Raise<NonEmptyList<InvalidField>>)
private fun NewComment.validated(): NewComment = accumulate {
    val body by accumulating { body.validBody() }
    NewComment(body)
}

const val MIN_FEED_LIMIT = 1
const val MIN_FEED_OFFSET = 0

data class InvalidFeedOffset(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "feed offset"
}

data class InvalidFeedLimit(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "feed limit"
}

context(_: RaiseAccumulate<String>)
private fun Int.minSize(size: Int): Int {
    ensureOrAccumulate(this >= size) { "too small, minimum is $size, and found $this" }
    return this
}

context(_: Raise<InvalidFeedOffset>)
fun Int.validFeedOffset(): FeedOffset =
    withError(::InvalidFeedOffset) {
        accumulate {
            minSize(MIN_FEED_OFFSET)
            FeedOffset(this@validFeedOffset)
        }
    }

context(_: Raise<InvalidFeedLimit>)
fun Int.validFeedLimit(): FeedLimit =
    withError(::InvalidFeedLimit) {
        accumulate {
            minSize(MIN_FEED_LIMIT)
            FeedLimit(this@validFeedLimit)
        }
    }

context(_: Raise<InvalidField>)
private fun Int.validFeedOffsetField(): FeedOffset =
    withError(::invalidFeedOffset) { validFeedOffset() }

context(_: Raise<InvalidField>)
private fun Int.validFeedLimitField(): FeedLimit =
    withError(::invalidFeedLimit) { validFeedLimit() }

context(_: Raise<IncorrectInput>)
fun ArticleResource.Feed.validate(userId: UserId): GetFeed =
    withError(::incorrectInput) { validated(userId) }

context(_: Raise<NonEmptyList<InvalidField>>)
private fun ArticleResource.Feed.validated(userId: UserId): GetFeed = accumulate {
    val offset by accumulating { offsetParam.validFeedOffsetField() }
    val limit by accumulating { limitParam.validFeedLimitField() }
    GetFeed(userId, limit.limit, offset.offset)
}

context(_: Raise<IncorrectInput>)
fun ArticlesResource.validate(currentUserId: UserId?): GetArticles =
    withError(::incorrectInput) { validated(currentUserId) }

context(_: Raise<NonEmptyList<InvalidField>>)
private fun ArticlesResource.validated(currentUserId: UserId?): GetArticles = accumulate {
    val offset by accumulating { offsetParam.validFeedOffsetField() }
    val limit by accumulating { limitParam.validFeedLimitField() }
    GetArticles(
        limit = limit.limit,
        offset = offset.offset,
        author = author,
        favorited = favorited,
        tag = tag,
        currentUserId = currentUserId,
    )
}
