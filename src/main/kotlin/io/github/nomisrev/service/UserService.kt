package io.github.nomisrev.service

import arrow.core.Either
import io.github.nefilim.kjwt.JWSAlgorithm
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nefilim.kjwt.KJWTSignError
import io.github.nefilim.kjwt.SignedJWT
import io.github.nefilim.kjwt.sign
import io.github.nomisrev.Config
import io.github.nomisrev.routes.GenericErrorModel
import io.github.nomisrev.routes.NewUser
import io.github.nomisrev.routes.UserInfo
import io.github.nomisrev.service.UserService.JwtFailure
import io.github.nomisrev.service.UserService.Unexpected
import io.github.nomisrev.service.UserService.UserExists
import io.github.nomisrev.sqldelight.UsersQueries
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

interface UserService {
  /** Registers the user and returns its unique identifier */
  suspend fun register(user: NewUser): Either<Error, Long>
  suspend fun generateJwtToken(userId: Long, password: String): Either<Error, String>
  suspend fun getUser(userId: Long): Either<Unexpected, UserInfo?>
  suspend fun getUser(username: String): Either<Unexpected, UserInfo?>

  sealed interface Error {
    val message: String
    fun toGenericErrorModel(): GenericErrorModel = GenericErrorModel(message)
  }

  data class JwtFailure(override val message: String) : Error
  data class UserExists(val user: NewUser) : Error {
    override val message: String = "$user already exists"
  }

  data class Unexpected(val error: Throwable) : Error {
    override val message: String =
      error.message ?: "Something went wrong. ${error::class.java} without message."
  }
}

fun userService(config: Config.Auth, usersQueries: UsersQueries) =
  object : UserService {
    @Suppress("MagicNumber") val defaultTokenLength = Duration.ofDays(30)

    override suspend fun register(user: NewUser): Either<UserService.Error, Long> =
      Either.catch {
        usersQueries.transactionWithResult<Long> {
          usersQueries.insert(user)
          usersQueries.selectId(user)
        }
      }
        .mapLeft { error ->
          if (error is PSQLException && error.sqlState == PSQLState.UNIQUE_VIOLATION.state) {
            UserExists(user)
          } else {
            Unexpected(error)
          }
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
private fun UsersQueries.insert(user: NewUser): Unit =
  insert(
    username = user.username,
    email = user.email,
    password = user.password,
    bio = "",
    image = ""
  )

// Overload selectId with our own domain
private fun UsersQueries.selectId(user: NewUser): Long =
  selectId(
      username = user.username,
      email = user.email,
      password = user.password,
      bio = "",
      image = ""
    )
    .executeAsOne()
