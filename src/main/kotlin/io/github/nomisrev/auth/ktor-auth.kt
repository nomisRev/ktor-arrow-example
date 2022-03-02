package io.github.nomisrev.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.nomisrev.config.Config
import io.github.nomisrev.service.UserService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal

/** Code if we'd use Ktor Auth versus our own middleware function */
private data class JWTUserId(val userId: Long) : Principal

// Get the userId
fun ApplicationCall.userIdOrNull(): Long? = principal<JWTUserId>()?.userId

// Installs the JWT based Authentication into Ktor
fun Application.configureJWT(config: Config.Auth, userService: UserService): Authentication =
  install(Authentication) {
    jwt {
      authSchemes("Token")
      verifier(JWT.require(Algorithm.HMAC512(config.secret)).withIssuer(config.issuer).build())
      validate { cred ->
        cred.payload.getClaim("id").asLong()?.let { id ->
          userService.getUser(id).orNull()?.let { JWTUserId(id) }
        }
      }
    }
  }
