@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.auth

import arrow.core.continuations.effect
import io.github.nomisrev.ApiError
import io.github.nomisrev.config.Config
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.routes.respond
import io.github.nomisrev.service.verifyJwtToken
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext

@JvmInline
value class JwtToken(val value: String)

data class JwtContext(val token: JwtToken, val userId: UserId)

// Small middleware to validate JWT token without using Ktor Auth / Nullable principle
context(UserPersistence, Config.Auth, PipelineContext<Unit, ApplicationCall>)
suspend inline fun jwtAuth( // BUG: inline + same context as lambda as function
  crossinline body: suspend /*context(PipelineContext<Unit, ApplicationCall>)*/ (JwtContext) -> Unit
) {
  optionalJwtAuth { context ->
    context?.let { body(it) } ?: call.respond(HttpStatusCode.Unauthorized)
  }
}

// TODO Report YT: BUG: inline + same context as lambda as function
context(PipelineContext<Unit, ApplicationCall>, UserPersistence, Config.Auth)
suspend inline fun optionalJwtAuth( // BUG: inline + same context as lambda as function
  crossinline body: suspend /*context(PipelineContext<Unit, ApplicationCall>)*/ (JwtContext?) -> Unit
) = effect<ApiError, JwtContext?> {
  jwtTokenStringOrNul()?.let { token ->
    val userId = verifyJwtToken(JwtToken(token))
    JwtContext(JwtToken(token), userId)
  }
}.fold(
  { error -> respond(error) },
  { context -> body(context) }
)

context(PipelineContext<Unit, ApplicationCall>)
fun jwtTokenStringOrNul(): String? =
  (call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)
    ?.blob
