package io.github.nomisrev.repo

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.computations.ensureNotNull
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.Unexpected
import io.github.nomisrev.ApiError.UserNotFound
import io.github.nomisrev.service.UserService.UserInfo
import io.github.nomisrev.sqldelight.UsersQueries
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

@JvmInline value class UserId(val serial: Long)

interface UserPersistence {
  /** Creates a new user in the database, and returns the [UserId] of the newly created user */
  suspend fun insert(username: String, email: String, password: String): Either<ApiError, UserId>

  /** Verifies is a password is correct for a given email */
  suspend fun verifyPassword(
    email: String,
    password: String
  ): Either<ApiError, Pair<UserId, UserInfo>>

  /** Select a User by its [UserId] */
  suspend fun select(userId: UserId): Either<ApiError, UserInfo>

  /** Select a User by its username */
  suspend fun select(username: String): Either<ApiError, UserInfo>

  @Suppress("LongParameterList")
  suspend fun update(
    userId: UserId,
    email: String?,
    username: String?,
    password: String?,
    bio: String?,
    image: String?
  ): Either<ApiError, UserInfo>
}

/** UserPersistence implementation based on SqlDelight and JavaX Crypto */
fun userPersistence(
  usersQueries: UsersQueries,
  defaultIterations: Int = 64000,
  defaultKeyLength: Int = 512,
  secretKeysFactory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
) =
  object : UserPersistence {

    override suspend fun insert(
      username: String,
      email: String,
      password: String
    ): Either<ApiError, UserId> {
      val salt = generateSalt()
      val key = generateKey(password, salt)
      return Either.catch {
          usersQueries.transactionWithResult<Long> {
            usersQueries.create(salt, key, username, email)
            usersQueries.selectId(username, email)
          }
        }
        .bimap(
          { error ->
            if (error is PSQLException && error.sqlState == PSQLState.UNIQUE_VIOLATION.state) {
              ApiError.UsernameAlreadyExists(username)
            } else {
              Unexpected("Failed to persist user: $username:$email", error)
            }
          },
          ::UserId
        )
    }

    override suspend fun verifyPassword(
      email: String,
      password: String
    ): Either<ApiError, Pair<UserId, UserInfo>> = either {
      val (userId, username, saltString, passwordString, bio, image) =
        ensureNotNull(usersQueries.selectSecurityByEmail(email).executeAsOneOrNull()) {
          UserNotFound("email=$email")
        }

      val salt = Base64.getDecoder().decode(saltString)
      val key = Base64.getDecoder().decode(passwordString)
      val hash = generateKey(password, salt)
      ensure(hash contentEquals key) { ApiError.PasswordNotMatched }
      Pair(UserId(userId), UserInfo(email, username, bio, image))
    }

    override suspend fun select(userId: UserId): Either<ApiError, UserInfo> = either {
      val userInfo =
        Either.catch {
            usersQueries
              .selectById(userId.serial) { email, username, _, _, bio, image ->
                UserInfo(email, username, bio, image)
              }
              .executeAsOneOrNull()
          }
          .mapLeft { e -> Unexpected("Failed to select user with userId: $userId", e) }
          .bind()
      ensureNotNull(userInfo) { UserNotFound("userId=$userId") }
    }

    override suspend fun select(username: String): Either<ApiError, UserInfo> = either {
      val userInfo =
        Either.catch { usersQueries.selectByUsername(username, ::UserInfo).executeAsOneOrNull() }
          .mapLeft { e -> Unexpected("Failed to select user with username: $username", e) }
          .bind()
      ensureNotNull(userInfo) { UserNotFound("username=$username") }
    }

    override suspend fun update(
      userId: UserId,
      email: String?,
      username: String?,
      password: String?,
      bio: String?,
      image: String?
    ): Either<ApiError, UserInfo> = either {
      val info =
        usersQueries.transactionWithResult<UserInfo?> {
          usersQueries.selectById(userId.serial).executeAsOneOrNull()?.let {
            (oldEmail, oldUsername, saltString, passwordString, oldBio, oldImage) ->
            val newPassword =
              password?.let { password ->
                val salt = Base64.getDecoder().decode(saltString)
                val key = Base64.getDecoder().decode(passwordString)
                val hash = generateKey(password, salt)
                if (hash contentEquals key) passwordString
                else Base64.getEncoder().encodeToString(hash)
              }
                ?: passwordString
            val newEmail = email ?: oldEmail
            val newUsername = username ?: oldUsername
            val newBio = bio ?: oldBio
            val newImage = image ?: oldImage
            usersQueries.update(newEmail, newUsername, saltString, newPassword, newBio, newImage)
            UserInfo(newEmail, newUsername, newBio, newImage)
          }
        }
      ensureNotNull(info) { UserNotFound("userId=$userId") }
    }

    private fun generateSalt(): ByteArray = UUID.randomUUID().toString().toByteArray()

    private fun generateKey(password: String, salt: ByteArray): ByteArray {
      val spec = PBEKeySpec(password.toCharArray(), salt, defaultIterations, defaultKeyLength)
      return secretKeysFactory.generateSecret(spec).encoded
    }
  }

private fun UsersQueries.create(
  salt: ByteArray,
  key: ByteArray,
  username: String,
  email: String
): Unit =
  insert(
    username = username,
    email = email,
    salt = Base64.getEncoder().encodeToString(salt),
    hashed_password = Base64.getEncoder().encodeToString(key),
    bio = "",
    image = ""
  )

private fun UsersQueries.selectId(username: String, email: String): Long =
  selectId(username = username, email = email, bio = "", image = "").executeAsOne()
