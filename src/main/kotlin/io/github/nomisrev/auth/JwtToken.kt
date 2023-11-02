package io.github.nomisrev.auth

import arrow.core.raise.effect
import arrow.core.raise.fold
import io.github.nomisrev.KtorCtx
import io.github.nomisrev.env.Env
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.routes.respond
import io.github.nomisrev.service.verifyJwtToken
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.call
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond

@JvmInline
value class JwtToken(val value: String)

// Small middleware to validate JWT token without using Ktor Auth / Nullable principle
context(KtorCtx, UserPersistence, Env.Auth)
suspend inline fun jwtAuth( // BUG: inline + same context as lambda as function
  crossinline body: suspend /*context(KtorCtx)*/ (token: JwtToken, userId: UserId) -> Unit
) {
  optionalJwtAuth { token, userId ->
    token?.let {
      userId?.let {
        body(token, userId)
      }
    } ?: call.respond(HttpStatusCode.Unauthorized)
  }
}

// TODO Report YT: BUG: inline + same context as lambda as function
context(KtorCtx, UserPersistence, Env.Auth)
suspend inline fun optionalJwtAuth( // BUG: inline + same context as lambda as function
  crossinline body: suspend /*context(KtorCtx)*/ (token: JwtToken?, userId: UserId?) -> Unit
) = effect {
  jwtTokenStringOrNul()?.let { token ->
    val userId = verifyJwtToken(JwtToken(token))
    Pair(JwtToken(token), userId)
  }
}.fold(
  { error -> respond(error) },
  { pair -> body(pair?.first, pair?.second) }
)

context(KtorCtx)
fun jwtTokenStringOrNul(): String? =
  (call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)
    ?.blob
