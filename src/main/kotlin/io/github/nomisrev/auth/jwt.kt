@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.auth

import arrow.core.Either
import arrow.core.raise.recover
import io.github.nomisrev.users.UserId
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import opensavvy.spine.api.FailureSpec
import opensavvy.spine.api.Parameters
import opensavvy.spine.server.TypedResponseScope

/**
 * TODO: This will be deprecated and made obsolete by Ktor Typed Auth Check
 *   https://github.com/ktorio/ktor-klip/pull/6 for details
 */
@JvmInline value class JwtToken(val value: String)

data class JwtContext(val token: JwtToken, val userId: UserId)

// Small middleware to validate JWT token without using Ktor Auth / Nullable principle
suspend inline fun RoutingContext.jwtAuth(
    jwtService: JwtService,
    crossinline body: suspend RoutingContext.(JwtContext) -> Unit,
) {
    optionalJwtAuth(jwtService) { context ->
        context?.let { body(this, it) } ?: call.respond(HttpStatusCode.Unauthorized)
    }
}

suspend inline fun RoutingContext.optionalJwtAuth(
    jwtService: JwtService,
    crossinline body: suspend RoutingContext.(JwtContext?) -> Unit,
) {
    call.jwtToken()?.let { token ->
        recover({
            val userId = jwtService.verifyJwtToken(JwtToken(token))
            body(this@optionalJwtAuth, JwtContext(JwtToken(token), userId))
        }) { error ->
            call.respond(error)
        }
    } ?: body(this, null)
}

suspend inline fun <A : Any, B : Any, C : FailureSpec, D : Parameters> TypedResponseScope<
    A,
    B,
    C,
    D,
>
    .optionalJwtAuth(
    jwtService: JwtService,
    crossinline body: suspend TypedResponseScope<A, B, C, D>.(JwtContext?) -> Unit,
) {
    call.jwtToken()?.let { token ->
        recover({
            val userId = jwtService.verifyJwtToken(JwtToken(token))
            body(this@optionalJwtAuth, JwtContext(JwtToken(token), userId))
        }) { error ->
            call.respond(error)
        }
    } ?: body(this, null)
}

suspend inline fun <A : Any, B : Any, C : FailureSpec, D : Parameters> TypedResponseScope<
    A,
    B,
    C,
    D,
>
    .jwtAuth(
    jwtService: JwtService,
    crossinline body: suspend TypedResponseScope<A, B, C, D>.(JwtContext) -> Unit,
) {
    optionalJwtAuth(jwtService) { context ->
        context?.let { body(this, it) } ?: call.respond(HttpStatusCode.Unauthorized)
    }
}

fun ApplicationCall.jwtToken(): String? =
    Either.catch { (request.parseAuthorizationHeader() as? HttpAuthHeader.Single) }
        .getOrNull()
        ?.blob
