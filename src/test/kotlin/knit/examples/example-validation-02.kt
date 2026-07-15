// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation02

context(_: Raise<InvalidPassword>)
fun String.password(): Password = TODO()

context(_: Raise<InvalidPassword>)
fun String.password(): Password = withError(::InvalidPassword) {
    accumulate {
        ensureOrAccumulate(isNotBlank()) { "Password cannot be blank" }
        ensureOrAccumulate(length >= 8) { "Password must be minimum 8 characters long" }
        ensureOrAccumulate(length <= 100) { "Password must be maximum 100 characters long" }
        ensureOrAccumulate(contains("[A-Z]".toRegex())) { "At least one uppercase letter" }
        ensureOrAccumulate(contains("[a-z]".toRegex())) { "At least one lowercase letter" }
        ensureOrAccumulate(contains("[0-9]".toRegex())) { "At least one number" }
        ensureOrAccumulate(contains("""[$#%&^*!?{}\[\]+=<€>±§|]""".toRegex())) { "At least one special character" }
        Password(this)
    }
}
import arrow.core.raise.context.RaiseAccumulate
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

private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@(.+)$")

@IgnorableReturnValue
context(_: RaiseAccumulate<String>)
private fun String.looksLikeEmail(): String = also {
    val _ = ensureOrAccumulate(emailPattern.matches(this)) { "'$this' is invalid email" }
}
