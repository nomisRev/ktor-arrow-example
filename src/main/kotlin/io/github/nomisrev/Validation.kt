package io.github.nomisrev

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.Accumulate
import arrow.core.raise.ExperimentalRaiseAccumulateApi
import arrow.core.raise.RaiseAccumulate.Value
import arrow.core.raise.RaiseDSL
import arrow.core.raise.accumulating
import arrow.core.raise.context.Raise
import arrow.core.raise.context.RaiseAccumulate
import arrow.core.raise.context.accumulate
import arrow.core.raise.ensureOrAccumulate
import arrow.core.raise.withError
import org.jetbrains.annotations.NotNull
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.AT_MOST_ONCE
import kotlin.contracts.contract
import kotlin.text.isNotBlank

context(raise: Raise<E>)
inline fun <E, E2, A> validate(
    transform: (NonEmptyList<E2>) -> E,
    block: context(Accumulate<E2>) () -> A
): A = raise.withError({ transform(it) }) { accumulate(block) }

context(raise: Raise<InvalidField>)
inline fun <A> validate(
    name: String,
    block: context(Accumulate<String>) () -> A
): A = raise.withError({ InvalidField(it, name) }) { accumulate(block) }

data class InvalidField(val errors: NonEmptyList<String>, val name: String) {
    constructor(error: String, name: String) : this(nonEmptyListOf(error), name)
}

@IgnorableReturnValue
context(_: Accumulate<String>)
fun String.notBlank(): String =
    also { ensureOrAccumulate(isNotBlank()) { "Cannot be blank" } }

@IgnorableReturnValue
context(_: Accumulate<String>)
fun String.minSize(size: Int): String =
    also { ensureOrAccumulate(length >= size) { "is too short (minimum is $size characters)" } }

@IgnorableReturnValue
context(_: Accumulate<String>)
fun String.maxSize(size: Int): String =
    also { ensureOrAccumulate(length <= size) { "is too long (maximum is $size characters)" } }

//
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

@Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")
@OptIn(ExperimentalContracts::class)
@ExperimentalRaiseAccumulateApi @RaiseDSL
context(raise: Accumulate<Error>)
inline fun <Error, A> accumulating(block: context(RaiseAccumulate<Error>) () -> A): Value<A> {
    contract { callsInPlace(block, AT_MOST_ONCE) }
    return raise.accumulating(block)
}
