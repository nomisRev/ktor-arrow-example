// This file was automatically generated from validation.md by Knit tool. Do not edit.
package io.github.nomisrev.knit.exampleValidation04

import arrow.core.NonEmptyList
import arrow.core.raise.context.Raise
import arrow.core.raise.context.RaiseAccumulate
import arrow.core.raise.context.ensureOrAccumulate
import arrow.core.raise.context.raise
import arrow.core.raise.recover

context(raise: Raise<Error>)
inline fun <Error, OtherError, A> withError(
    transform: (OtherError) -> Error,
    block: context(Raise<OtherError>) () -> A
): A = recover(block) { raise(transform(it)) }

context(raise: Raise<NonEmptyList<Error>>)
inline fun <Error, A> accumulate(block: context(RaiseAccumulate<Error>) () -> A): A = TODO()

context(_: RaiseAccumulate<String>)
fun String.validate() {
    ensureOrAccumulate(isNotBlank()) { "Password cannot be blank" }
    ensureOrAccumulate(length >= 8) { "Password must be minimum 8 characters long" }
    ensureOrAccumulate(length <= 100) { "Password must be maximum 100 characters long" }
    ensureOrAccumulate(contains("[A-Z]".toRegex())) { "At least one uppercase letter" }
    ensureOrAccumulate(contains("[a-z]".toRegex())) { "At least one lowercase letter" }
    ensureOrAccumulate(contains("[0-9]".toRegex())) { "At least one number" }
    ensureOrAccumulate(contains("""[$#%&^*!?{}\[\]+=<€>±§|]""".toRegex())) { "At least one special character" }
}
