package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.continuations.EffectScope
import io.github.nefilim.kjwt.JWSAlgorithm
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nefilim.kjwt.KJWTSignError
import io.github.nefilim.kjwt.SignedJWT
import io.github.nefilim.kjwt.sign
import io.github.nomisrev.ensureNotNull
import io.github.nomisrev.DomainErrors
import io.github.nomisrev.JwtError
import io.github.nomisrev.JwtGeneration
import io.github.nomisrev.JwtInvalid
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.env.Env
import io.github.nomisrev.persistence.UserId
import io.github.nomisrev.persistence.UserPersistence
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.toJavaDuration

/** Generate a new JWT token for userId and password. Doesn't invalidate old password */
context(EffectScope<JwtError>, Env.Auth)
suspend fun generateJwtToken(userId: UserId): JwtToken =
  JWT
    .hs512 {
      val now = LocalDateTime.now(ZoneId.of("UTC"))
      issuedAt(now)
      expiresAt(now + duration.toJavaDuration())
      issuer(issuer)
      claim("id", userId.serial)
    }
    .sign(secret)
    .bind()
    .let { JwtToken(it.rendered) }

/** Verify a JWT token. Checks if userId exists in database, and token is not expired. */
context(DomainErrors, UserPersistence)
suspend fun verifyJwtToken(token: JwtToken): UserId {
  val jwt =
    JWT.decodeT(token.value, JWSHMAC512Algorithm).mapLeft { JwtInvalid(it.toString()) }.bind()
  val userId =
    ensureNotNull(jwt.claimValueAsLong("id").orNull()) {
      JwtInvalid("id missing from JWT Token")
    }
  val expiresAt =
    ensureNotNull(jwt.expiresAt().orNull()) { JwtInvalid("exp missing from JWT Token") }
  ensure(expiresAt.isAfter(LocalDateTime.now())) { JwtInvalid("JWT Token expired") }
  select(UserId(userId))
  return UserId(userId)
}

context(EffectScope<JwtError>)
private suspend fun <A : JWSAlgorithm> Either<KJWTSignError, SignedJWT<A>>.bind(): SignedJWT<A> =
  mapLeft { jwtError ->
    when (jwtError) {
      KJWTSignError.InvalidKey -> JwtGeneration("JWT singing error: invalid Secret Key.")
      KJWTSignError.InvalidJWTData ->
        JwtGeneration("JWT singing error: Generated with incorrect JWT data")
      is KJWTSignError.SigningError -> JwtGeneration("JWT singing error: ${jwtError.cause}")
    }
  }.bind()
