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
import io.github.nomisrev.service.UserService.Unexpected
import io.github.nomisrev.service.UserService.UserDoesNotExist
import io.github.nomisrev.service.UserService.UserExists
import io.github.nomisrev.service.UserService.UserInfo
import io.github.nomisrev.sqldelight.UsersQueries
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

interface UserService {
  /** Registers the user and returns its unique identifier */
  suspend fun register(username: String, email: String, password: String): Either<Error, Long>
  /** Logs in a user based on email and password. */
  suspend fun login(email: String, password: String): Either<Error, Pair<Long, UserInfo>>
  /** Generate a new JWT token for userId and password. Doesn't invalidate old passwordd */
  suspend fun generateJwtToken(userId: Long, password: String): Either<Error, String>
  /** Verify a JWT token. Checks if userId exists in database, and token is not expired. */
  suspend fun verifyJwtToken(token: String): Either<Nel<KJWTError>, Long>
  /** Retrieve used based on userId */
  suspend fun getUser(userId: Long): Either<Unexpected, UserInfo?>
  /** Retrieve used based on username */
  suspend fun getUser(username: String): Either<Unexpected, UserInfo?>

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

  data class UserDoesNotExist(val email: String) : Error {
    override val message: String = "$email does not exists"
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
    ): Either<UserService.Error, Long> =
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

    override suspend fun login(
      email: String,
      password: String
    ): Either<UserService.Error, Pair<Long, UserInfo>> = either {
      val (userId, username, saltString, passwordString, bio, image) =
        ensureNotNull(usersQueries.selectSecurityByEmail(email).executeAsOneOrNull()) {
          UserDoesNotExist(email)
        }
      val salt = Base64.getDecoder().decode(saltString)
      val key = Base64.getDecoder().decode(passwordString)
      val hash = generateKey(password, salt)
      if (hash.contentEquals(key)) {
        Pair(userId, UserInfo(email, username, bio, image))
      } else IncorrectLoginCredentials.left().bind()
    }

    override suspend fun generateJwtToken(
      userId: Long,
      password: String
    ): Either<UserService.Error, String> =
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
        .map(SignedJWT<JWSHMAC512Algorithm>::rendered)

    override suspend fun getUser(userId: Long): Either<Unexpected, UserInfo?> =
      Either.catch { usersQueries.selectById(userId, ::UserInfo).executeAsOneOrNull() }
        .mapLeft(::Unexpected)

    override suspend fun getUser(username: String): Either<Unexpected, UserInfo?> =
      Either.catch { usersQueries.selectByUsername(username, ::UserInfo).executeAsOneOrNull() }
        .mapLeft(::Unexpected)

    override suspend fun verifyJwtToken(token: String): Either<Nel<KJWTError>, Long> = either {
      withContext(Dispatchers.IO) {
        val jwt = JWT.decodeT(token, JWSHMAC512Algorithm).mapLeft(KJWTVerificationError::nel).bind()
        ClaimsVerification.validateClaims(isNotExpired(), issuer(config.issuer), userIdExists())(
            jwt
          )
          .bind()
        ensureNotNull(jwt.claimValueAsLong("id").orNull()) { RequiredClaimIsInvalid("id").nel() }
      }
    }

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

private fun <A : JWSAlgorithm> Either<KJWTSignError, SignedJWT<A>>.toUserServiceError() =
    mapLeft { jwtError ->
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
