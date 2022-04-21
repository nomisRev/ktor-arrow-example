package io.github.nomisrev.service

import arrow.core.continuations.EffectScope
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.EmptyUpdate
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.validate

data class RegisterUser(val username: String, val email: String, val password: String)

data class Update(
  val userId: UserId,
  val username: String?,
  val email: String?,
  val password: String?,
  val bio: String?,
  val image: String?
)

data class Login(val email: String, val password: String)

data class UserInfo(val email: String, val username: String, val bio: String, val image: String)

interface UserService {
  /** Registers the user and returns its unique identifier */
  context(EffectScope<ApiError>)
  suspend fun register(input: RegisterUser): JwtToken

  /** Updates a user with all the provided fields, returns resulting info */
  context(EffectScope<ApiError>)
  suspend fun update(input: Update): UserInfo

  /** Logs in a user based on email and password. */
  context(EffectScope<ApiError>)
  suspend fun login(input: Login): Pair<JwtToken, UserInfo>

  /** Retrieve used based on userId */
  context(EffectScope<ApiError>)
  suspend fun getUser(userId: UserId): UserInfo

  /** Retrieve used based on username */
  context(EffectScope<ApiError>)
  suspend fun getUser(username: String): UserInfo
}

fun userService(repo: UserPersistence, jwtService: JwtService) =
  object : UserService {
    context(EffectScope<ApiError>)
    override suspend fun register(input: RegisterUser): JwtToken {
      val (username, email, password) = input.validate().bind()
      val userId = repo.insert(username, email, password)
      return jwtService.generateJwtToken(userId)
    }

    context(EffectScope<ApiError>)
    override suspend fun login(input: Login): Pair<JwtToken, UserInfo> {
      val (email, password) = input.validate().bind()
      val (userId, info) = repo.verifyPassword(email, password)
      val token = jwtService.generateJwtToken(userId)
      return Pair(token, info)
    }

    context(EffectScope<ApiError>)
    override suspend fun update(input: Update): UserInfo {
      val (userId, username, email, password, bio, image) = input.validate().bind()
      ensure(email != null || username != null || bio != null || image != null) {
        EmptyUpdate("Cannot update user with $userId with only null values")
      }
      return repo.update(userId, email, username, password, bio, image)
    }

    context(EffectScope<ApiError>)
    override suspend fun getUser(userId: UserId): UserInfo =
      repo.select(userId)

    context(EffectScope<ApiError>)
    override suspend fun getUser(username: String): UserInfo =
      repo.select(username)
  }
