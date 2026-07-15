// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation04

import arrow.core.NonEmptyList
import arrow.core.raise.context.Raise
import arrow.core.raise.context.withError
import arrow.core.raise.context.RaiseAccumulate
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.ensureOrAccumulate

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

context(_: Raise<NonEmptyList<String>>)
private fun String.passwordRules(): String = accumulate {
    notBlank()
    minSize(8)
    maxSize(100)
}

sealed interface InvalidField {
    val errors: NonEmptyList<String>
    val field: String
}

data class InvalidPassword(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "password"
}

context(_: Raise<InvalidField>)
private fun String.passwordValidation(): String =
    withError(::InvalidPassword) { passwordRules() }
