package io.github.nomisrev

import arrow.core.ValidatedNel
import arrow.core.invalidNel
import arrow.core.valid
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext

typealias KtorCtx = PipelineContext<*, ApplicationCall>

fun <E> KtorCtx.query(name: String, notPresent: () -> E): ValidatedNel<E, String> =
  call.request.queryParameters[name]?.valid() ?: notPresent().invalidNel()

fun <E> KtorCtx.path(name: String, notPresent: () -> E): ValidatedNel<E, String> =
  call.parameters[name]?.valid() ?: notPresent().invalidNel()

suspend fun KtorCtx.ok(any: Any) = call.respond(HttpStatusCode.OK, any)

suspend fun KtorCtx.badRequest(any: Any) = call.respond(HttpStatusCode.BadRequest, any)
