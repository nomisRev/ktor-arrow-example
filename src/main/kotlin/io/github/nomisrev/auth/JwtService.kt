package io.github.nomisrev.auth

import arrow.core.raise.context.Raise
import arrow.core.raise.context.bind
import arrow.core.raise.context.withError
import com.auth0.jwt.JWT.require
import com.auth0.jwt.algorithms.Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nefilim.kjwt.KJWTSignError
import io.github.nefilim.kjwt.sign
import io.github.nomisrev.JwtGeneration
import io.github.nomisrev.env.Env
import io.github.nomisrev.users.UserId
import io.github.nomisrev.users.UserPersistence
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.parseAuthorizationHeader
import java.time.Clock
import java.time.Instant
import kotlin.time.toJavaDuration

class JwtService(private val env: Env.Auth, private val repo: UserPersistence) {
    val config: JwtConfig<JwtContext> =
        JwtConfig(require(Algorithm.HMAC512(env.secret)).withIssuer(env.issuer).build()) {
            val id = it.getClaim("id", Long::class)
            id?.let {
                (request.parseAuthorizationHeader() as? HttpAuthHeader.Single)?.let {
                    JwtContext(JwtToken(it.blob), UserId(id))
                }
            }
        }

    /** Generate a new JWT token for userId. Doesn't invalidate old password */
    context(_: Raise<JwtGeneration>)
    fun generateJwtToken(userId: UserId): JwtToken {
        val signedJwt =
            withError(KJWTSignError::toJwtGeneration) {
                JWT.hs512 {
                        val now = Instant.now(Clock.systemUTC())
                        issuedAt(now)
                        expiresAt(now + env.duration.toJavaDuration())
                        issuer(env.issuer)
                        claim("id", userId.serial)
                    }
                    .sign(env.secret)
                    .bind()
            }

        return JwtToken(signedJwt.rendered)
    }
}

private fun KJWTSignError.toJwtGeneration() =
    when (this) {
        KJWTSignError.InvalidKey -> JwtGeneration("JWT singing error: invalid Secret Key.")
        KJWTSignError.InvalidJWTData ->
            JwtGeneration("JWT singing error: Generated with incorrect JWT data")

        is KJWTSignError.SigningError -> JwtGeneration("JWT singing error: ${cause}")
    }
