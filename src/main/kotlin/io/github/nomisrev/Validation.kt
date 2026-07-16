@file:OptIn(ExperimentalRaiseAccumulateApi::class)
@file:Suppress("TooManyFunctions")

package io.github.nomisrev

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Accumulate
import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.RaiseAccumulate.Value
import arrow.core.raise.RaiseDSL
import arrow.core.raise.context.Raise
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.ensure
import arrow.core.raise.context.withError
import arrow.core.raise.ensureOrAccumulate
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.AT_MOST_ONCE
import kotlin.contracts.contract
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
        operator fun invoke(value: String): Password = withError(::InvalidPassword) {
            accumulate {
                value.notBlank()
                value.minSize(MIN_PASSWORD_LENGTH)
                value.maxSize(MAX_PASSWORD_LENGTH)
                ensureOrAccumulate(value.contains(uppercase)) { "At least one uppercase letter" }
                ensureOrAccumulate(value.contains(lowercase)) { "At least one lowercase letter" }
                ensureOrAccumulate(value.contains(number)) { "At least one number" }
                ensureOrAccumulate(value.contains(special)) { "At least one special character" }
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
        private val emailPattern = ".+@.+\\..+".toRegex()

        context(_: Raise<InvalidEmail>)
        operator fun invoke(value: String): Email = withError(::InvalidEmail) {
            val normalized = value.trim()
            accumulate {
                normalized.notBlank()
                normalized.maxSize(MAX_EMAIL_LENGTH)
                val _ = ensureOrAccumulate(emailPattern.matches(normalized)) {
                    "'$normalized' is invalid email"
                }
                Email(normalized)
            }
        }
    }
}

@JvmInline
value class Username private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidUsername>)
        operator fun invoke(value: String): Username = withError(::InvalidUsername) {
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

@JvmInline
value class Title private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidTitle>)
        operator fun invoke(value: String): Title = Title(value.trimNotBlank(::InvalidTitle))
    }
}

@JvmInline
value class Description private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidDescription>)
        operator fun invoke(value: String): Description =
            Description(value.trimNotBlank(::InvalidDescription))
    }
}

@JvmInline
value class Body private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidBody>)
        operator fun invoke(value: String): Body = Body(value.trimNotBlank(::InvalidBody))
    }
}

context(_: Raise<E>)
private fun <E> String.trimNotBlank(withError: (String) -> E): String = withError(withError) {
    val value = trim()
    ensure(value.isNotBlank()) { "Cannot be blank" }
    value
}

@IgnorableReturnValue
context(_: Accumulate<String>)
fun String.notBlank(): String = also {
    ensureOrAccumulate(isNotBlank()) { "Cannot be blank" }
}

@IgnorableReturnValue
context(_: Accumulate<String>)
private fun String.minSize(size: Int): String = also {
    ensureOrAccumulate(length >= size) { "is too short (minimum is $size characters)" }
}

@IgnorableReturnValue
context(_: Accumulate<String>)
private fun String.maxSize(size: Int): String = also {
    ensureOrAccumulate(length <= size) { "is too long (maximum is $size characters)" }
}

data class InvalidFeedOffset(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "feed offset"
}

data class InvalidFeedLimit(override val errors: NonEmptyList<String>) : InvalidField {
    constructor(error: String) : this(nonEmptyListOf(error))

    override val field: String = "feed limit"
}

@Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")
@OptIn(ExperimentalContracts::class)
@ExperimentalRaiseAccumulateApi
@RaiseDSL
@IgnorableReturnValue
context(raise: Accumulate<Error>)
inline fun <Error> ensureOrAccumulate(condition: Boolean, error: () -> Error): Value<Unit> {
    contract { callsInPlace(error, AT_MOST_ONCE) }
    return raise.ensureOrAccumulate(condition, error)
}
