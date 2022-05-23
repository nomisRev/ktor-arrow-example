package io.github.nomisrev.auth

import arrow.core.continuations.effect
import io.github.nomisrev.DomainError
import io.github.nomisrev.KtorCtx
import io.github.nomisrev.config.Env
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.routes.respond
import io.github.nomisrev.service.verifyJwtToken
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.call
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond as ktorRespond

@JvmInline
value class JwtToken(val value: String)

data class JwtContext(val token: JwtToken, val userId: UserId)

// Small middleware to validate JWT token without using Ktor Auth / Nullable principle
context(KtorCtx, UserPersistence, Env.Auth)
suspend inline fun jwtAuth( // BUG: inline + same context as lambda as function
  crossinline body: suspend /*context(KtorCtx)*/ (JwtContext) -> Unit
) {
  optionalJwtAuth { context ->
    context?.let { body(it) } ?: call.ktorRespond(HttpStatusCode.Unauthorized)
  }
}

// TODO Report YT: BUG: inline + same context as lambda as function
context(KtorCtx, UserPersistence, Env.Auth)
suspend inline fun optionalJwtAuth( // BUG: inline + same context as lambda as function
  crossinline body: suspend /*context(KtorCtx)*/ (JwtContext?) -> Unit
) = effect<DomainError, JwtContext?> {
  jwtTokenStringOrNul()?.let { token ->
    val userId = verifyJwtToken(JwtToken(token))
    JwtContext(JwtToken(token), userId)
  }
}.fold(
  { error -> respond(error) },
  { context -> body(context) }
)

context(KtorCtx)
fun jwtTokenStringOrNul(): String? =
  (call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)
    ?.blob
