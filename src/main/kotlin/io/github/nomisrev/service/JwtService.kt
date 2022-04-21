package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.computations.ensureNotNull
import arrow.core.continuations.EffectScope
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import io.github.nefilim.kjwt.JWSAlgorithm
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nefilim.kjwt.KJWTSignError
import io.github.nefilim.kjwt.SignedJWT
import io.github.nefilim.kjwt.sign
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.JwtGeneration
import io.github.nomisrev.ApiError.JwtInvalid
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.config.Config
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.toJavaDuration

interface JwtService {
  /** Generate a new JWT token for userId and password. Doesn't invalidate old password */
  context(EffectScope<ApiError>)
  suspend fun generateJwtToken(userId: UserId): JwtToken

  /** Verify a JWT token. Checks if userId exists in database, and token is not expired. */
  context(EffectScope<ApiError>)
  suspend fun verifyJwtToken(token: JwtToken): UserId
}

fun jwtService(config: Config.Auth, repo: UserPersistence) =
  object : JwtService {
    context(EffectScope<ApiError>)
    override suspend fun generateJwtToken(userId: UserId): JwtToken =
      JWT
        .hs512 {
          val now = LocalDateTime.now(ZoneId.of("UTC"))
          issuedAt(now)
          expiresAt(now + config.duration.toJavaDuration())
          issuer(config.issuer)
          claim("id", userId.serial)
        }
        .sign(config.secret)
        .bind()
        .let { JwtToken(it.rendered) }

    context(EffectScope<ApiError>)
    override suspend fun verifyJwtToken(token: JwtToken): UserId {
      val jwt =
        JWT.decodeT(token.value, JWSHMAC512Algorithm).mapLeft { JwtInvalid(it.toString()) }.bind()
      val userId =
        ensureNotNull(jwt.claimValueAsLong("id").orNull()) {
          JwtInvalid("id missing from JWT Token")
        }
      val expiresAt =
        ensureNotNull(jwt.expiresAt().orNull()) { JwtInvalid("exp missing from JWT Token") }
      ensure(expiresAt.isAfter(LocalDateTime.now())) { JwtInvalid("JWT Token expired") }
      repo.select(UserId(userId))
      return UserId(userId)
    }
  }

context(EffectScope<ApiError>)
private suspend fun <A : JWSAlgorithm> Either<KJWTSignError, SignedJWT<A>>.bind(): SignedJWT<A> =
  mapLeft { jwtError ->
    when (jwtError) {
      KJWTSignError.InvalidKey -> JwtGeneration("JWT singing error: invalid Secret Key.")
      KJWTSignError.InvalidJWTData ->
        JwtGeneration("JWT singing error: Generated with incorrect JWT data")
      is KJWTSignError.SigningError -> JwtGeneration("JWT singing error: ${jwtError.cause}")
    }
  }.bind()
