package io.github.nomisrev

import arrow.core.continuations.EffectScope
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <A, B, C, R> with(a: A, b: B, c: C, block: context(A, B, C) (TypeWrapper<C>) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block(a, b, c, TypeWrapper.IMPL)
}

sealed interface TypeWrapper<out A> {
    object IMPL: TypeWrapper<Nothing>
}

// TODO - temp fix for ambiguity bug in compiler
context(EffectScope<R>)
@OptIn(ExperimentalContracts::class)
public suspend fun <R, B : Any> ensureNotNull(value: B?, shift: () -> R): B {
    contract { returns() implies (value != null) }
    return value ?: shift(shift())
}
