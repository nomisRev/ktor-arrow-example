package io.github.nomisrev.utils

import arrow.core.Either
import io.github.nomisrev.service.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext

// Small middleware to validate JWT token without using Ktor Auth / Nullable principle
suspend fun PipelineContext<Unit, ApplicationCall>.jwtAuth(
  userService: UserService,
  optional: Boolean = false,
  body: suspend PipelineContext<Unit, ApplicationCall>.(JwtContext) -> Unit
) {
  val token = jwtToken()
  when {
    token == null && !optional -> call.respond(HttpStatusCode.Unauthorized)
    token != null ->
      userService
        .verifyJwtToken(token)
        .fold(
          { errors ->
            //  We could return all JWT errors here, but it's not in the OpenApi Spec
            //  call.respond(HttpStatusCode.Unauthorized, errors.toGenericError())
            call.respond(HttpStatusCode.Unauthorized)
          },
          { userId -> body(this, JwtContext(token, userId)) }
        )
    else -> Unit // token == null && optional
  }
}

data class JwtContext(val token: String, val userId: Long)

private fun PipelineContext<Unit, ApplicationCall>.jwtToken(): String? =
  Either.catch { (call.request.parseAuthorizationHeader() as HttpAuthHeader.Single) }.orNull()?.blob

// private fun Nel<KJWTError>.toGenericError(): GenericErrorModel =
//    GenericErrorModel(GenericErrorModelErrors(map(KJWTError::toString)))
