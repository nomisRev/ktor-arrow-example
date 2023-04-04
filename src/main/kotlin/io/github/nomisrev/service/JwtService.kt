package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.github.nefilim.kjwt.JWSAlgorithm
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nefilim.kjwt.KJWTSignError
import io.github.nefilim.kjwt.SignedJWT
import io.github.nefilim.kjwt.sign
import io.github.nomisrev.DomainError
import io.github.nomisrev.JwtGeneration
import io.github.nomisrev.JwtInvalid
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.env.Env
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import java.time.Clock
import java.time.Instant
import kotlin.time.toJavaDuration

/** Generate a new JWT token for userId and password. Doesn't invalidate old password */
context(Raise<DomainError>, Env.Auth)
fun generateJwtToken(userId: UserId): JwtToken =
  JWT
    .hs512 {
      val now = Instant.now(Clock.systemUTC())
      issuedAt(now)
      expiresAt(now + duration.toJavaDuration())
      issuer(issuer)
      claim("id", userId.serial)
    }
    .sign(secret)
    .bind()
    .let { JwtToken(it.rendered) }

/** Verify a JWT token. Checks if userId exists in database, and token is not expired. */
context(Raise<DomainError>, UserPersistence)
suspend fun verifyJwtToken(token: JwtToken): UserId {
  val jwt =
    JWT.decodeT(token.value, JWSHMAC512Algorithm).mapLeft { JwtInvalid(it.toString()) }.bind()
  val userId =
    ensureNotNull(jwt.claimValueAsLong("id").getOrNull()) {
      JwtInvalid("id missing from JWT Token")
    }
  val expiresAt =
    ensureNotNull(jwt.expiresAt().getOrNull()) { JwtInvalid("exp missing from JWT Token") }
  ensure(expiresAt.isAfter(Instant.now(Clock.systemUTC()))) { JwtInvalid("JWT Token expired") }
  select(UserId(userId))
  return UserId(userId)
}

context(Raise<DomainError>)
private fun <A : JWSAlgorithm> Either<KJWTSignError, SignedJWT<A>>.bind(): SignedJWT<A> =
  mapLeft { jwtError ->
    when (jwtError) {
      KJWTSignError.InvalidKey -> JwtGeneration("JWT singing error: invalid Secret Key.")
      KJWTSignError.InvalidJWTData ->
        JwtGeneration("JWT singing error: Generated with incorrect JWT data")
      is KJWTSignError.SigningError -> JwtGeneration("JWT singing error: ${jwtError.cause}")
    }
  }.bind()
