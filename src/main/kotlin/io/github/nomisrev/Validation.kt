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
import io.github.nomisrev.articles.FeedLimit
import io.github.nomisrev.articles.FeedOffset
import io.github.nomisrev.articles.NewArticle
import io.github.nomisrev.articles.NewComment
import io.github.nomisrev.users.Login
import io.github.nomisrev.users.RegisterUser
import io.github.nomisrev.users.Update

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

context(_: Raise<IncorrectInput>)
fun Login.validate(): Login = withError(::IncorrectInput) { validated() }

context(_: Raise<NonEmptyList<InvalidField>>)
private fun Login.validated(): Login = accumulate {
    val email by accumulating { email.validEmail() }
    val password by accumulating { password.validPassword() }
    Login(email, password)
}

context(_: Raise<IncorrectInput>)
fun RegisterUser.validate(): RegisterUser =
    withError(::IncorrectInput) {
        accumulate {
            val username by accumulating { username.validUsername() }
            val email by accumulating { email.validEmail() }
            val password by accumulating { password.validPassword() }
            RegisterUser(username, email, password)
        }
    }

context(_: Raise<IncorrectInput>)
fun Update.validate(): Update =
    withError(::IncorrectInput) {
        accumulate {
            val username by accumulating { username?.validUsername() }
            val email by accumulating { email?.validEmail() }
            val password by accumulating { password?.validPassword() }
            Update(userId, username, email, password, bio, image)
        }
    }

private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 100
private const val MAX_EMAIL_LENGTH = 350
private const val MIN_USERNAME_LENGTH = 1
private const val MAX_USERNAME_LENGTH = 25

context(_: Raise<InvalidField>)
private fun String.validPassword(): String = passwordValidation()

context(_: Raise<InvalidField>)
private fun String.passwordValidation(): String = withError(::InvalidPassword) { passwordRules() }

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
private fun String.emailValidation(): String = withError(::InvalidEmail) { trim().emailRules() }

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
    withError(::InvalidUsername) { trim().usernameRules() }

context(_: Raise<NonEmptyList<String>>)
private fun String.usernameRules(): String = accumulate {
    notBlank()
    minSize(MIN_USERNAME_LENGTH)
    maxSize(MAX_USERNAME_LENGTH)
    this@usernameRules
}

context(_: Raise<InvalidField>)
private fun String.validTitle(): String = withError(::InvalidTitle) { trim().notBlankRule() }

context(_: Raise<InvalidField>)
private fun String.validDescription(): String =
    withError(::InvalidDescription) { trim().notBlankRule() }

context(_: Raise<InvalidField>)
private fun String.validBody(): String = withError(::InvalidBody) { trim().notBlankRule() }

context(_: Raise<NonEmptyList<String>>)
private fun String.notBlankRule(): String = accumulate {
    notBlank()
    this@notBlankRule
}

context(_: Raise<InvalidField>)
private fun List<String>.validTags(): Set<String> =
    withError(::InvalidTag) { mapOrAccumulate { it.trim().notBlank() }.toSet() }

@IgnorableReturnValue
context(_: RaiseAccumulate<String>)
private fun String.notBlank(): String = also {
    val _ = ensureOrAccumulate(isNotBlank()) { "Cannot be blank" }
}

@IgnorableReturnValue
context(_: RaiseAccumulate<String>)
private fun String.minSize(size: Int): String = also {
    val _ = ensureOrAccumulate(length >= size) { "is too short (minimum is $size characters)" }
}

@IgnorableReturnValue
context(_: RaiseAccumulate<String>)
private fun String.maxSize(size: Int): String = also {
    val _ = ensureOrAccumulate(length <= size) { "is too long (maximum is $size characters)" }
}

private val emailPattern = ".+@.+\\..+".toRegex()

@IgnorableReturnValue
context(_: RaiseAccumulate<String>)
private fun String.looksLikeEmail(): String = also {
    val _ = ensureOrAccumulate(emailPattern.matches(this)) { "'$this' is invalid email" }
}

context(_: Raise<IncorrectInput>)
fun NewArticle.validate(): NewArticle =
    withError(::IncorrectInput) {
        accumulate {
            val title by accumulating { title.validTitle() }
            val description by accumulating { description.validDescription() }
            val body by accumulating { body.validBody() }
            val tagList by accumulating { tagList.validTags().toList() }
            NewArticle(title, description, body, tagList)
        }
    }

context(_: Raise<IncorrectInput>)
fun NewComment.validate(): NewComment = withError(::IncorrectInput) { validated() }

context(_: Raise<NonEmptyList<InvalidField>>)
private fun NewComment.validated(): NewComment = accumulate {
    val body by accumulating { body.validBody() }
    NewComment(body)
}

private const val MIN_FEED_LIMIT = 1
private const val MIN_FEED_OFFSET = 0

data class InvalidFeedOffset(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "feed offset"
}

data class InvalidFeedLimit(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "feed limit"
}

@IgnorableReturnValue
context(_: RaiseAccumulate<String>)
private fun Int.minSize(size: Int): Int = also {
    val _ = ensureOrAccumulate(this >= size) { "too small, minimum is $size, and found $this" }
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
