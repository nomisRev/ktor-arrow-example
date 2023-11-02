package io.github.nomisrev

import io.ktor.server.application.ApplicationCall
import io.ktor.util.pipeline.PipelineContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract

typealias KtorCtx = PipelineContext<Unit, ApplicationCall>

// Work-around for bug with context receiver lambda
// https://youtrack.jetbrains.com/issue/KT-51243
@OptIn(ExperimentalContracts::class)
@Suppress("SUBTYPING_BETWEEN_CONTEXT_RECEIVERS", "LongParameterList")
inline fun <A, B, C, D, E, F, R> with(
  a: A,
  b: B,
  c: C,
  d: D,
  e: E,
  f: F,
  block: context(A, B, C, D, E, F) (TypePlacedHolder<F>) -> R
): R {
  contract { callsInPlace(block, EXACTLY_ONCE) }
  return block(a, b, c, d, e, f, TypePlacedHolder)
}

sealed interface TypePlacedHolder<out A> {
  companion object : TypePlacedHolder<Nothing>
}
