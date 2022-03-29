package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.Nel
import arrow.core.computations.either
import arrow.core.computations.ensureNotNull
import arrow.core.left
import arrow.core.nel
import io.github.nefilim.kjwt.ClaimsValidator
import io.github.nefilim.kjwt.ClaimsVerification
import io.github.nefilim.kjwt.ClaimsVerification.issuer
import io.github.nefilim.kjwt.ClaimsVerification.requiredOptionClaim
import io.github.nefilim.kjwt.JWSAlgorithm
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nefilim.kjwt.KJWTError
import io.github.nefilim.kjwt.KJWTSignError
import io.github.nefilim.kjwt.KJWTValidationError.RequiredClaimIsInvalid
import io.github.nefilim.kjwt.KJWTVerificationError
import io.github.nefilim.kjwt.SignedJWT
import io.github.nefilim.kjwt.sign
import io.github.nomisrev.config.Config
import io.github.nomisrev.routes.GenericErrorModel
import io.github.nomisrev.service.UserService.IncorrectLoginCredentials
import io.github.nomisrev.service.UserService.JwtFailure
import io.github.nomisrev.service.UserService.JwtToken
import io.github.nomisrev.service.UserService.Unexpected
import io.github.nomisrev.service.UserService.UserDoesNotExist
import io.github.nomisrev.service.UserService.UserExists
import io.github.nomisrev.service.UserService.UserInfo
import io.github.nomisrev.service.UserService.UserWithIdDoesNotExist
import io.github.nomisrev.sqldelight.UsersQueries
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.Error
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

interface UserService {
  /** Registers the user and returns its unique identifier */
  suspend fun register(username: String, email: String, password: String): Either<Error, JwtToken>
  /** Logs in a user based on email and password. */
  suspend fun login(email: String, password: String): Either<Error, Pair<JwtToken, UserInfo>>
  /** Generate a new JWT token for userId and password. Doesn't invalidate old password */
  suspend fun generateJwtToken(userId: Long, password: String): Either<Error, JwtToken>
  /** Verify a JWT token. Checks if userId exists in database, and token is not expired. */
  suspend fun verifyJwtToken(token: JwtToken): Either<Nel<JwtFailure>, Long>
  /** Retrieve used based on userId */
  suspend fun getUser(userId: Long): Either<Error, UserInfo>
  /** Retrieve used based on username */
  suspend fun getUser(username: String): Either<Error, UserInfo>

  @JvmInline value class JwtToken(val value: String)

  /** Decoupled domain from API */
  data class UserInfo(val email: String, val username: String, val bio: String, val image: String)

  sealed interface Error {
    val message: String
    fun toGenericErrorModel(): GenericErrorModel = GenericErrorModel(message)
  }

  data class JwtFailure(override val message: String) : Error
  data class UserExists(val username: String) : Error {
    override val message: String = "$username already exists"
  }

  data class UserDoesNotExist(val property: String) : Error {
    override val message: String = "user with $property does not exists"
  }

  data class UserWithIdDoesNotExist(val userId: Long) : Error {
    override val message: String = "user with id $userId does not exists"
  }

  object IncorrectLoginCredentials : Error {
    override val message: String = "Credentials are not correct"
  }

  data class Unexpected(val error: Throwable) : Error {
    override val message: String =
      error.message ?: "Something went wrong. ${error::class.java} without message."
  }
}

/**
 * UserService impl based on:
 * - SqlDelight UsersQueries for persistence
 * - kjwt for JWT generation, decoding and validation
 * - javax.crypto for safely storing passwords
 */
fun userService(config: Config.Auth, usersQueries: UsersQueries) =
  object : UserService {
    @Suppress("MagicNumber") val defaultTokenLength = Duration.ofDays(30)

    // Password hashing
    val secretKeysFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    @Suppress("MagicNumber") val defaultIterations = 64000
    @Suppress("MagicNumber") val defaultKeyLength = 512

    override suspend fun register(
      username: String,
      email: String,
      password: String
    ): Either<UserService.Error, JwtToken> = either {
      val userId =
        Either.catch {
            usersQueries.transactionWithResult<Long> {
              val salt = generateSalt()
              val key = generateKey(password, salt)
              usersQueries.insert(salt, key, username, email)
              usersQueries.selectId(username, email)
            }
          }
          .mapLeft { error ->
            if (error is PSQLException && error.sqlState == PSQLState.UNIQUE_VIOLATION.state) {
              UserExists(username)
            } else {
              Unexpected(error)
            }
          }
          .bind()
      generateJwtToken(userId, password).bind()
    }

    override suspend fun login(
      email: String,
      password: String
    ): Either<UserService.Error, Pair<JwtToken, UserInfo>> = either {
      val (userId, username, saltString, passwordString, bio, image) =
        ensureNotNull(usersQueries.selectSecurityByEmail(email).executeAsOneOrNull()) {
          UserDoesNotExist(email)
        }
      val salt = Base64.getDecoder().decode(saltString)
      val key = Base64.getDecoder().decode(passwordString)
      val hash = generateKey(password, salt)
      if (hash.contentEquals(key)) {
        val token = generateJwtToken(userId, password).bind()
        Pair(token, UserInfo(email, username, bio, image))
      } else IncorrectLoginCredentials.left().bind()
    }

    override suspend fun generateJwtToken(
      userId: Long,
      password: String
    ): Either<UserService.Error, JwtToken> =
      JWT
        .hs512 {
          val now = LocalDateTime.now(ZoneId.of("UTC"))
          issuedAt(now)
          expiresAt(now + defaultTokenLength)
          issuer(config.issuer)
          claim("id", userId)
        }
        .sign(config.secret)
        .toUserServiceError()
        .map { JwtToken(it.rendered) }

    override suspend fun getUser(userId: Long): Either<UserService.Error, UserInfo> = either {
      val userInfo =
        Either.catch { usersQueries.selectById(userId, ::UserInfo).executeAsOneOrNull() }
          .mapLeft(::Unexpected)
          .bind()
      ensureNotNull(userInfo) { UserWithIdDoesNotExist(userId) }
    }

    override suspend fun getUser(username: String): Either<UserService.Error, UserInfo> = either {
      val userInfo =
        Either.catch { usersQueries.selectByUsername(username, ::UserInfo).executeAsOneOrNull() }
          .mapLeft(::Unexpected)
          .bind()
      ensureNotNull(userInfo) { UserDoesNotExist(username) }
    }

    override suspend fun verifyJwtToken(token: JwtToken): Either<Nel<JwtFailure>, Long> =
      either<Nel<KJWTError>, Long> {
        withContext(Dispatchers.IO) {
          val jwt =
            JWT.decodeT(token.value, JWSHMAC512Algorithm).mapLeft(KJWTVerificationError::nel).bind()
          ClaimsVerification.validateClaims(isNotExpired(), issuer(config.issuer), userIdExists())(
              jwt
            )
            .bind()
          ensureNotNull(jwt.claimValueAsLong("id").orNull()) { RequiredClaimIsInvalid("id").nel() }
        }
      }
        .mapLeft { errors -> errors.map { JwtFailure(it.toString()) } }

    private fun generateSalt(): ByteArray = UUID.randomUUID().toString().toByteArray()

    private fun generateKey(password: String, salt: ByteArray): ByteArray {
      val spec = PBEKeySpec(password.toCharArray(), salt, defaultIterations, defaultKeyLength)
      return secretKeysFactory.generateSecret(spec).encoded
    }

    private fun isNotExpired(): ClaimsValidator =
      requiredOptionClaim("exp", { expiresAt() }, { it.isAfter(LocalDateTime.now()) })

    private fun userIdExists(): ClaimsValidator =
      requiredOptionClaim(
        "id",
        { claimValueAsLong("id") },
        { id -> usersQueries.selectById(id).executeAsOneOrNull()?.let { true } ?: false }
      )
  }

private fun <A : JWSAlgorithm> Either<KJWTSignError, SignedJWT<A>>.toUserServiceError():
  Either<JwtFailure, SignedJWT<A>> = mapLeft { jwtError ->
  when (jwtError) {
    KJWTSignError.InvalidKey -> JwtFailure("Server error: invalid Secret Key.")
    KJWTSignError.InvalidJWTData -> JwtFailure("Server error: generated incorrect JWT")
    is KJWTSignError.SigningError ->
      JwtFailure("Server error: something went wrong singing the JWT")
  }
}

// Overload insert with our own domain
private fun UsersQueries.insert(
  salt: ByteArray,
  key: ByteArray,
  username: String,
  email: String
): Unit {
  insert(
    username = username,
    email = email,
    salt = Base64.getEncoder().encodeToString(salt),
    hashed_password = Base64.getEncoder().encodeToString(key),
    bio = "",
    image = ""
  )
}

// Overload selectId with our own domain
private fun UsersQueries.selectId(username: String, email: String): Long =
  selectId(username = username, email = email, bio = "", image = "").executeAsOne()
