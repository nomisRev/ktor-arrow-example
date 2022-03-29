@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.auth

import arrow.core.Either
import arrow.core.Nel
import io.github.nomisrev.routes.GenericErrorModel
import io.github.nomisrev.routes.GenericErrorModelErrors
import io.github.nomisrev.service.UserService
import io.github.nomisrev.service.UserService.JwtFailure
import io.github.nomisrev.service.UserService.JwtToken
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext

data class JwtContext(val token: String, val userId: Long)

// Small middleware to validate JWT token without using Ktor Auth / Nullable principle
suspend fun PipelineContext<Unit, ApplicationCall>.jwtAuth(
  userService: UserService,
  body: suspend PipelineContext<Unit, ApplicationCall>.(JwtContext) -> Unit
) {
  jwtToken()?.let { token -> jwtAuth(userService, token, body) }
    ?: call.respond(HttpStatusCode.Unauthorized)
}

suspend fun PipelineContext<Unit, ApplicationCall>.optionalJwtAuth(
  userService: UserService,
  body: suspend PipelineContext<Unit, ApplicationCall>.(JwtContext?) -> Unit
) {
  jwtToken()?.let { token -> jwtAuth(userService, token, body) } ?: body(this, null)
}

private suspend fun PipelineContext<Unit, ApplicationCall>.jwtAuth(
  userService: UserService,
  token: String,
  body: suspend PipelineContext<Unit, ApplicationCall>.(JwtContext) -> Unit
) {
  userService
    .verifyJwtToken(JwtToken(token))
    .fold(
      { errors -> call.respond(HttpStatusCode.UnprocessableEntity, errors.toGenericError()) },
      { userId -> body(this, JwtContext(token, userId)) }
    )
}

private fun PipelineContext<Unit, ApplicationCall>.jwtToken(): String? =
  Either.catch { (call.request.parseAuthorizationHeader() as HttpAuthHeader.Single) }.orNull()?.blob

private fun Nel<JwtFailure>.toGenericError(): GenericErrorModel =
  GenericErrorModel(GenericErrorModelErrors(map(JwtFailure::toString)))
