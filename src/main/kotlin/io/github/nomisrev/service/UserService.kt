package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.computations.either
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.EmptyUpdate
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.service.UserService.UserInfo

interface UserService {
  /** Registers the user and returns its unique identifier */
  suspend fun register(
    username: String,
    email: String,
    password: String
  ): Either<ApiError, JwtToken>

  @Suppress("LongParameterList")
  /** Updates a user with all the provided fields, returns resulting info */
  suspend fun update(
    userId: UserId,
    email: String?,
    username: String?,
    password: String?,
    bio: String?,
    image: String?
  ): Either<ApiError, UserInfo>

  /** Logs in a user based on email and password. */
  suspend fun login(email: String, password: String): Either<ApiError, Pair<JwtToken, UserInfo>>

  /** Retrieve used based on userId */
  suspend fun getUser(userId: UserId): Either<ApiError, UserInfo>

  /** Retrieve used based on username */
  suspend fun getUser(username: String): Either<ApiError, UserInfo>

  /** Decoupled domain from API */
  data class UserInfo(val email: String, val username: String, val bio: String, val image: String)
}

fun userService(repo: UserPersistence, jwtService: JwtService) =
  object : UserService {
    override suspend fun register(
      username: String,
      email: String,
      password: String
    ): Either<ApiError, JwtToken> = either {
      val userId = repo.insert(username, email, password).bind().serial
      jwtService.generateJwtToken(userId, password).bind()
    }

    override suspend fun login(
      email: String,
      password: String
    ): Either<ApiError, Pair<JwtToken, UserInfo>> = either {
      val (userId, info) = repo.verifyPassword(email, password).bind()
      val token = jwtService.generateJwtToken(userId.serial, password).bind()
      Pair(token, info)
    }

    override suspend fun getUser(userId: UserId): Either<ApiError, UserInfo> = repo.select(userId)

    override suspend fun getUser(username: String): Either<ApiError, UserInfo> =
      repo.select(username)

    override suspend fun update(
      userId: UserId,
      email: String?,
      username: String?,
      password: String?,
      bio: String?,
      image: String?
    ): Either<ApiError, UserInfo> = either {
      ensure(email != null || username != null || bio != null || image != null) {
        EmptyUpdate("Cannot update user with $userId with only null values")
      }
      repo.update(userId, email, username, password, bio, image).bind()
    }
  }
