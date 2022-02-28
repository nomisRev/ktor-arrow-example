package io.github.nomisrev

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal

private data class JWTUserId(val userId: Long) : Principal

// Get the userId
fun ApplicationCall.userIdOrNull(): Long? = principal<JWTUserId>()?.userId

// Installs the JWT based Authentication into Ktor
fun Application.configureJWT(config: Config.Auth): Authentication =
  install(Authentication) {
    jwt {
      authSchemes("Token")
      verifier(JWT.require(Algorithm.HMAC512(config.secret)).withIssuer(config.issuer).build())
      validate { cred -> cred.payload.getClaim("id").asLong()?.let { id -> JWTUserId(id) } }
    }
  }
