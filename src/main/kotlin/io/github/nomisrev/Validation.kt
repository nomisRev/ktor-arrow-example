@file:OptIn(ExperimentalRaiseAccumulateApi::class)
@file:Suppress("TooManyFunctions")

package io.github.nomisrev

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.context.Raise
import arrow.core.raise.context.RaiseAccumulate
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.accumulating
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureOrAccumulate
import arrow.core.raise.context.mapOrAccumulate
import arrow.core.raise.context.withError
import io.github.nomisrev.articles.ArticlesParameters
import io.github.nomisrev.articles.FeedLimit
import io.github.nomisrev.articles.FeedOffset
import io.github.nomisrev.articles.FeedParameters
import io.github.nomisrev.articles.GetArticles
import io.github.nomisrev.articles.GetFeed
import io.github.nomisrev.articles.NewArticle
import io.github.nomisrev.articles.NewComment
import io.github.nomisrev.users.UserId
import kotlin.text.contains
import kotlin.text.isNotBlank
import kotlin.text.trim

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
    constructor(error: String) : this(nonEmptyListOf(error))

    override val field: String = "title"
}

data class InvalidDescription(override val errors: NonEmptyList<String>) : InvalidField {
    constructor(error: String) : this(nonEmptyListOf(error))

    override val field: String = "description"
}

data class InvalidBody(override val errors: NonEmptyList<String>) : InvalidField {
    constructor(error: String) : this(nonEmptyListOf(error))

    override val field: String = "body"
}

private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 100
private const val MAX_EMAIL_LENGTH = 350
private const val MIN_USERNAME_LENGTH = 1
private const val MAX_USERNAME_LENGTH = 25

@JvmInline
value class Password private constructor(private val value: String) {
    fun raw(): String = value

    override fun toString(): String = "Password(*****)"

    companion object {
        context(_: Raise<InvalidPassword>)
        operator fun invoke(value: String): Password =
            withError(::InvalidPassword) {
                accumulate {
                    value.notBlank()
                    value.minSize(MIN_PASSWORD_LENGTH)
                    value.maxSize(MAX_PASSWORD_LENGTH)
                    val _ =
                        ensureOrAccumulate(value.contains(uppercase)) {
                            "At least one uppercase letter"
                        }
                    val _ =
                        ensureOrAccumulate(value.contains(lowercase)) {
                            "At least one lowercase letter"
                        }
                    val _ = ensureOrAccumulate(value.contains(number)) { "At least one number" }
                    val _ =
                        ensureOrAccumulate(value.contains(special)) {
                            "At least one special character"
                        }
                    Password(value)
                }
            }
    }
}

private val uppercase = "[A-Z]".toRegex()
private val lowercase = "[a-z]".toRegex()
private val number = "[0-9]".toRegex()
private val special = """[$#%&^*!?{}\[\]+=<€>±§|]""".toRegex()

@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidEmail>)
        operator fun invoke(value: String): Email =
            withError(::InvalidEmail) {
                val normalized = value.trim()
                accumulate {
                    normalized.notBlank()
                    normalized.maxSize(MAX_EMAIL_LENGTH)
                    normalized.looksLikeEmail()
                    Email(normalized)
                }
            }
    }
}

@JvmInline
value class Username private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidUsername>)
        operator fun invoke(value: String): Username =
            withError(::InvalidUsername) {
                val normalized = value.trim()
                accumulate {
                    normalized.notBlank()
                    normalized.minSize(MIN_USERNAME_LENGTH)
                    normalized.maxSize(MAX_USERNAME_LENGTH)
                    Username(normalized)
                }
            }
    }
}

context(_: Raise<InvalidTitle>)
private fun String.validTitle(): String = trimNotBlank(::InvalidTitle)

context(_: Raise<InvalidDescription>)
private fun String.validDescription(): String = trimNotBlank(::InvalidDescription)

context(_: Raise<InvalidBody>)
private fun String.validBody(): String = trimNotBlank(::InvalidBody)

// TODO: Check inference problem and report to YouTrack.
//  IntelliJ suggest it's not needed but when removed report ambuigity.
//  Context parameter inference should infer OtherError == String, this should disambiguate
// InvalidBody constructor
//    withError<InvalidField, String, String>(::InvalidBody) {
//        val value = trim()
//        ensure(value.isNotBlank()) { "Cannot be blank" }
//        value
//    }

context(_: Raise<E>)
private fun <E> String.trimNotBlank(withError: (String) -> E): String =
    withError(withError) {
        val value = trim()
        ensure(value.isNotBlank()) { "Cannot be blank" }
        value
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

context(_: Raise<IncorrectInput>)
fun FeedParameters.validate(userId: UserId): GetFeed =
    withError(::IncorrectInput) {
        accumulate {
            val offset by accumulating { offset.validFeedOffset() }
            val limit by accumulating { limit.validFeedLimit() }
            GetFeed(userId, limit.limit, offset.offset)
        }
    }

context(_: Raise<IncorrectInput>)
fun ArticlesParameters.validate(currentUserId: UserId?): GetArticles =
    withError(::IncorrectInput) {
        accumulate {
            val offset by accumulating { offset.validFeedOffset() }
            val limit by accumulating { limit.validFeedLimit() }
            GetArticles(
                limit = limit.limit,
                offset = offset.offset,
                author = author,
                favorited = favorited,
                tag = tag,
                currentUserId = currentUserId,
            )
        }
    }
