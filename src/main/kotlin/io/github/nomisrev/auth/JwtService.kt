package io.github.nomisrev.auth

import arrow.core.raise.context.Raise
import arrow.core.raise.context.bind
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.withError
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nefilim.kjwt.KJWTSignError
import io.github.nefilim.kjwt.sign
import io.github.nomisrev.DomainError
import io.github.nomisrev.JwtGeneration
import io.github.nomisrev.JwtInvalid
import io.github.nomisrev.env.Env
import io.github.nomisrev.users.UserId
import io.github.nomisrev.users.UserPersistence
import java.time.Clock
import java.time.Instant
import kotlin.time.toJavaDuration

class JwtService(
    private val env: Env.Auth,
    private val repo: UserPersistence,
) {
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

    context(_: Raise<DomainError>)
    fun verifyJwtToken(token: JwtToken): UserId {
        val jwt =
            withError({ JwtInvalid(it.toString()) }) {
                JWT.decodeT(token.value, JWSHMAC512Algorithm).bind()
            }
        val id =
            ensureNotNull(jwt.claimValueAsLong("id").getOrNull()) {
                JwtInvalid("id missing from JWT Token")
            }
        val expiresAt =
            ensureNotNull(jwt.expiresAt().getOrNull()) { JwtInvalid("exp missing from JWT Token") }
        ensure(expiresAt.isAfter(Instant.now(Clock.systemUTC()))) {
            JwtInvalid("JWT Token expired")
        }
        repo.select(UserId(id))
        return UserId(id)
    }
}

private fun KJWTSignError.toJwtGeneration() =
    when (this) {
        KJWTSignError.InvalidKey -> JwtGeneration("JWT singing error: invalid Secret Key.")
        KJWTSignError.InvalidJWTData ->
            JwtGeneration("JWT singing error: Generated with incorrect JWT data")
        is KJWTSignError.SigningError -> JwtGeneration("JWT singing error: ${cause}")
    }
