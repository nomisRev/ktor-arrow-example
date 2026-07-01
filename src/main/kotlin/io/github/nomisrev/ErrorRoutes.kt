package io.github.nomisrev

import arrow.core.raise.recover
import io.ktor.server.routing.Route
import io.ktor.utils.io.KtorDsl
import kotlinx.serialization.Serializable
import opensavvy.spine.api.Endpoint
import opensavvy.spine.api.FailureSpec.ByCode
import opensavvy.spine.api.FailureSpec.Never
import opensavvy.spine.api.FailureSpec.Or
import opensavvy.spine.api.Parameters
import opensavvy.spine.server.TypedResponseScope
import opensavvy.spine.server.fail
import opensavvy.spine.server.route

@Serializable data class GenericErrorModel(val errors: GenericErrorModelErrors)

@Serializable data class GenericErrorModelErrors(val body: List<String>)

@Suppress("DEPRECATION_ERROR", "DSL_MARKER_APPLIED_TO_WRONG_TARGET")
@KtorDsl
inline fun <
    reified In : Any,
    reified Out : Any,
    reified Failure : Or<Never, ByCode<GenericErrorModel>>,
    reified Params : Parameters,
> Route.route(
    endpoint: Endpoint<In, Out, Failure, Params>,
    crossinline block:
        suspend context(arrow.core.raise.Raise<DomainError>) TypedResponseScope<
            In,
            Out,
            Or<Never, ByCode<GenericErrorModel>>,
            Params,
        >.() -> Unit,
): Unit =
    route(endpoint) response@{
        recover(
            block = { block() },
            recover = { error: DomainError -> fail(error.toGenericErrorModel()) },
        )
    }
