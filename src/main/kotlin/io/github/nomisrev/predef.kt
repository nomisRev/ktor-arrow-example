package io.github.nomisrev

import arrow.core.continuations.EffectScope
import io.ktor.server.application.ApplicationCall
import io.ktor.util.pipeline.PipelineContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract

typealias KtorCtx = PipelineContext<Unit, ApplicationCall>

// Work-around for bug with context receiver lambda
// https://youtrack.jetbrains.com/issue/KT-51243
@OptIn(ExperimentalContracts::class)
inline fun <A, B, R> with(a: A, b: B, block: context(A, B) (TypePlacedHolder<B>) -> R): R {
    contract { callsInPlace(block, EXACTLY_ONCE) }
    return block(a, b, TypePlacedHolder)
}

@OptIn(ExperimentalContracts::class)
inline fun <A, B, C, R> with(a: A, b: B, c: C, block: context(A, B, C) (TypePlacedHolder<C>) -> R): R {
    contract { callsInPlace(block, EXACTLY_ONCE) }
    return block(a, b, c, TypePlacedHolder)
}

sealed interface TypePlacedHolder<out A> {
    companion object : TypePlacedHolder<Nothing>
}

// TODO - temp fix for ambiguity bug in compiler
context(EffectScope<R>)
@OptIn(ExperimentalContracts::class)
suspend fun <R, B : Any> ensureNotNull(value: B?, shift: () -> R): B {
    contract { returns() implies (value != null) }
    return value ?: shift(shift())
}
