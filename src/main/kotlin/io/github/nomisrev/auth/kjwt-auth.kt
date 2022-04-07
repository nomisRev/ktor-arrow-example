@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.auth

import arrow.core.Either
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.routes.respond
import io.github.nomisrev.service.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext

@JvmInline value class JwtToken(val value: String)

data class JwtContext(val token: JwtToken, val userId: UserId)

// Small middleware to validate JWT token without using Ktor Auth / Nullable principle
suspend fun PipelineContext<Unit, ApplicationCall>.jwtAuth(
  jwtService: JwtService,
  body: suspend PipelineContext<Unit, ApplicationCall>.(JwtContext) -> Unit
) {
  jwtToken()?.let { token -> jwtAuth(jwtService, token, body) }
    ?: call.respond(HttpStatusCode.Unauthorized)
}

suspend fun PipelineContext<Unit, ApplicationCall>.optionalJwtAuth(
  jwtService: JwtService,
  body: suspend PipelineContext<Unit, ApplicationCall>.(JwtContext?) -> Unit
) {
  jwtToken()?.let { token -> jwtAuth(jwtService, token, body) } ?: body(this, null)
}

private suspend fun PipelineContext<Unit, ApplicationCall>.jwtAuth(
  jwtService: JwtService,
  token: String,
  body: suspend PipelineContext<Unit, ApplicationCall>.(JwtContext) -> Unit
) {
  jwtService
    .verifyJwtToken(JwtToken(token))
    .fold(
      { error -> respond(error) },
      { userId -> body(this, JwtContext(JwtToken(token), userId)) }
    )
}

private fun PipelineContext<Unit, ApplicationCall>.jwtToken(): String? =
  Either.catch { (call.request.parseAuthorizationHeader() as HttpAuthHeader.Single) }.orNull()?.blob
