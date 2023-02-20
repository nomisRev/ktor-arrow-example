package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.raise.either
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

interface JwtService {
  /** Generate a new JWT token for userId and password. Doesn't invalidate old password */
  suspend fun generateJwtToken(userId: UserId): Either<JwtGeneration, JwtToken>

  /** Verify a JWT token. Checks if userId exists in database, and token is not expired. */
  suspend fun verifyJwtToken(token: JwtToken): Either<DomainError, UserId>
}

fun jwtService(env: Env.Auth, repo: UserPersistence) =
  object : JwtService {
    override suspend fun generateJwtToken(userId: UserId): Either<JwtGeneration, JwtToken> =
      JWT.hs512 {
          val now = Instant.now(Clock.systemUTC())
          issuedAt(now)
          expiresAt(now + env.duration.toJavaDuration())
          issuer(env.issuer)
          claim("id", userId.serial)
        }
        .sign(env.secret)
        .toUserServiceError()
        .map { JwtToken(it.rendered) }

    override suspend fun verifyJwtToken(token: JwtToken): Either<DomainError, UserId> = either {
      val jwt =
        JWT.decodeT(token.value, JWSHMAC512Algorithm).mapLeft { JwtInvalid(it.toString()) }.bind()
      val userId =
        ensureNotNull(jwt.claimValueAsLong("id").orNull()) {
          JwtInvalid("id missing from JWT Token")
        }
      val expiresAt =
        ensureNotNull(jwt.expiresAt().orNull()) { JwtInvalid("exp missing from JWT Token") }
      ensure(expiresAt.isAfter(Instant.now(Clock.systemUTC()))) { JwtInvalid("JWT Token expired") }
      repo.select(UserId(userId)).bind()
      UserId(userId)
    }
  }

private fun <A : JWSAlgorithm> Either<KJWTSignError, SignedJWT<A>>.toUserServiceError():
  Either<JwtGeneration, SignedJWT<A>> = mapLeft { jwtError ->
  when (jwtError) {
    KJWTSignError.InvalidKey -> JwtGeneration("JWT singing error: invalid Secret Key.")
    KJWTSignError.InvalidJWTData ->
      JwtGeneration("JWT singing error: Generated with incorrect JWT data")
    is KJWTSignError.SigningError -> JwtGeneration("JWT singing error: ${jwtError.cause}")
  }
}
