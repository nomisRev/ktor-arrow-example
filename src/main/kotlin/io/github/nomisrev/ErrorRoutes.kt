@file:Suppress("DEPRECATION_ERROR")

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

@Serializable
data class GenericErrorModel(val errors: GenericErrorModelErrors)

@Serializable
data class GenericErrorModelErrors(val body: List<String>)

/**
 * This is a function that allows us to remove a layer of indentation and repition in every route.
 * Normally we'd have to write:
 *
 * ```kotlin
 * routeWithRaise(Api.Articles.list) {
 *     withError({ e: DomainError -> e.toGenericErrorModel() }) {
 *         val input = parameters.validate(call.principal?.userId)
 *         val articles = articleService.getAllArticles(input)
 *         respond(articles)
 *     }
 * }
 * ```
 *
 * To manually transform our fine-grained `DomainError` into the Real World Conduit API [GenericErrorModel].
 * Using this extension, we can bake-in `{ e: DomainError -> e.toGenericErrorModel() }`.
 * This allows our `Route` to take [Api]'s defined with `GenericErrorModel` and run our [DomainErrors] instead.
 *
 * ```kotlin
 * route(Api.Articles.list) {
 *     val input = parameters.validate(call.principal?.userId)
 *     val articles = articleService.getAllArticles(input)
 *     respond(articles)
 * }
 * ```
 */
@Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")
@KtorDsl
inline fun <
        reified In : Any,
        reified Out : Any,
        reified Failure : Or<Never, ByCode<GenericErrorModel>>,
        reified Params : Parameters
        >
        Route.route(
    endpoint: Endpoint<In, Out, Failure, Params>,
    crossinline block: suspend context(DomainErrors) TypedResponseScope<
            In,
            Out,
            Or<Never, ByCode<GenericErrorModel>>,
            Params,
            >.() -> Unit,
): Unit = route(endpoint) response@{
    recover(
        block = { block() },
        recover = { error: DomainError -> fail(error.toGenericErrorModel()) },
    )
}
