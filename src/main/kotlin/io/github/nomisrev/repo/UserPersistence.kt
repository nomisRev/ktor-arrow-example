package io.github.nomisrev.repo

import arrow.core.Either
import arrow.core.computations.ensureNotNull
import arrow.core.continuations.EffectScope
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.Unexpected
import io.github.nomisrev.ApiError.UserNotFound
import io.github.nomisrev.service.UserInfo
import io.github.nomisrev.sqldelight.UsersQueries
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

@JvmInline value class UserId(val serial: Long)

interface UserPersistence {
  /** Creates a new user in the database, and returns the [UserId] of the newly created user */
  context(EffectScope<ApiError>)
  suspend fun insert(username: String, email: String, password: String): UserId

  /** Verifies is a password is correct for a given email */
  context(EffectScope<ApiError>)
  suspend fun verifyPassword(
    email: String,
    password: String
  ): Pair<UserId, UserInfo>

  /** Select a User by its [UserId] */
  context(EffectScope<ApiError>)
  suspend fun select(userId: UserId): UserInfo

  /** Select a User by its username */
  context(EffectScope<ApiError>)
  suspend fun select(username: String): UserInfo

  context(EffectScope<ApiError>)
  @Suppress("LongParameterList")
  suspend fun update(
    userId: UserId,
    email: String?,
    username: String?,
    password: String?,
    bio: String?,
    image: String?
  ): UserInfo
}

/** UserPersistence implementation based on SqlDelight and JavaX Crypto */
fun userPersistence(
  usersQueries: UsersQueries,
  defaultIterations: Int = 64000,
  defaultKeyLength: Int = 512,
  secretKeysFactory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
) =
  object : UserPersistence {

    context(EffectScope<ApiError>)
    override suspend fun insert(
      username: String,
      email: String,
      password: String
    ): UserId {
      val salt = generateSalt()
      val key = generateKey(password, salt)
      return Either.catch { usersQueries.create(salt, key, username, email) }.mapLeft { error ->
        if (error is PSQLException && error.sqlState == PSQLState.UNIQUE_VIOLATION.state) {
          ApiError.UsernameAlreadyExists(username)
        } else {
          Unexpected("Failed to persist user: $username:$email", error)
        }
      }.bind()
    }

    context(EffectScope<ApiError>)
    override suspend fun verifyPassword(
      email: String,
      password: String
    ): Pair<UserId, UserInfo> {
      val (userId, username, salt, key, bio, image) =
        ensureNotNull(usersQueries.selectSecurityByEmail(email).executeAsOneOrNull()) {
          UserNotFound("email=$email")
        }

      val hash = generateKey(password, salt)
      ensure(hash contentEquals key) { ApiError.PasswordNotMatched }
      return Pair(userId, UserInfo(email, username, bio, image))
    }

    context(EffectScope<ApiError>)
    override suspend fun select(userId: UserId): UserInfo {
      val userInfo =
        Either.catch {
            usersQueries
              .selectById(userId) { email, username, _, _, bio, image ->
                UserInfo(email, username, bio, image)
              }
              .executeAsOneOrNull()
          }
          .mapLeft { e -> Unexpected("Failed to select user with userId: $userId", e) }
          .bind()
      return ensureNotNull(userInfo) { UserNotFound("userId=$userId") }
    }

    context(EffectScope<ApiError>)
    override suspend fun select(username: String): UserInfo {
      val userInfo =
        Either.catch { usersQueries.selectByUsername(username, ::UserInfo).executeAsOneOrNull() }
          .mapLeft { e -> Unexpected("Failed to select user with username: $username", e) }
          .bind()
      return ensureNotNull(userInfo) { UserNotFound("username=$username") }
    }

    context(EffectScope<ApiError>)
    override suspend fun update(
      userId: UserId,
      email: String?,
      username: String?,
      password: String?,
      bio: String?,
      image: String?
    ): UserInfo {
      val info =
        usersQueries.transactionWithResult<UserInfo?> {
          usersQueries.selectById(userId).executeAsOneOrNull()?.let {
            (oldEmail, oldUsername, salt, oldPassword, oldBio, oldImage) ->
            val newPassword = password?.let { generateKey(it, salt) } ?: oldPassword
            val newEmail = email ?: oldEmail
            val newUsername = username ?: oldUsername
            val newBio = bio ?: oldBio
            val newImage = image ?: oldImage
            usersQueries.update(newEmail, newUsername, newPassword, newBio, newImage, userId)
            UserInfo(newEmail, newUsername, newBio, newImage)
          }
        }
      return ensureNotNull(info) { UserNotFound("userId=$userId") }
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
): UserId =
  insertAndGetId(
      username = username,
      email = email,
      salt = salt,
      hashed_password = key,
      bio = "",
      image = ""
    )
    .executeAsOne()
