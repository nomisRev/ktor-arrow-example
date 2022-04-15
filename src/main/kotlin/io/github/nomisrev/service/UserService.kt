package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.continuations.either
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.EmptyUpdate
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.validate

data class RegisterUser(val username: String, val email: String, val password: String)

data class UpdateUser(
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
  suspend fun register(input: RegisterUser): Either<ApiError, JwtToken>

  /** Updates a user with all the provided fields, returns resulting info */
  suspend fun update(input: UpdateUser): Either<ApiError, UserInfo>

  /** Logs in a user based on email and password. */
  suspend fun login(input: Login): Either<ApiError, Pair<JwtToken, UserInfo>>

  /** Retrieve used based on userId */
  suspend fun getUser(userId: UserId): Either<ApiError, UserInfo>

  /** Retrieve used based on username */
  suspend fun getUser(username: String): Either<ApiError, UserInfo>
}

fun userService(repo: UserPersistence, jwtService: JwtService) =
  object : UserService {
    override suspend fun register(input: RegisterUser): Either<ApiError, JwtToken> = either {
      val (username, email, password) = input.validate().bind()
      val userId = repo.insert(username, email, password).bind()
      jwtService.generateJwtToken(userId).bind()
    }

    override suspend fun login(input: Login): Either<ApiError, Pair<JwtToken, UserInfo>> = either {
      val (email, password) = input.validate().bind()
      val (userId, info) = repo.verifyPassword(email, password).bind()
      val token = jwtService.generateJwtToken(userId).bind()
      Pair(token, info)
    }

    override suspend fun update(input: UpdateUser): Either<ApiError, UserInfo> = either {
      val (userId, username, email, password, bio, image) = input.validate().bind()
      ensure(email != null || username != null || bio != null || image != null) {
        EmptyUpdate("Cannot update user with $userId with only null values")
      }
      repo.update(userId, email, username, password, bio, image).bind()
    }

    override suspend fun getUser(userId: UserId): Either<ApiError, UserInfo> = repo.select(userId)

    override suspend fun getUser(username: String): Either<ApiError, UserInfo> =
      repo.select(username)
  }
