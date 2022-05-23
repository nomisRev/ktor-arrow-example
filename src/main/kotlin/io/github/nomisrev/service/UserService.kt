package io.github.nomisrev.service

import arrow.core.continuations.EffectScope
import io.github.nomisrev.ApiError
import io.github.nomisrev.UserError
import io.github.nomisrev.JwtError
import io.github.nomisrev.ValidationError
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.validate
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.config.Config
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence

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

/** Registers the user and returns its unique identifier */
context(
  EffectScope<JwtError>,
  EffectScope<ValidationError>,
  EffectScope<UserError>,
  UserPersistence,
  Config.Auth
)
suspend fun register(input: RegisterUser): JwtToken {
  val (username, email, password) = input.validate().bind()
  val userId = insert(username, email, password)
  return generateJwtToken(userId)
}

/** Logs in a user based on email and password. */
//context(
//  EffectScope<JwtError>,
//  EffectScope<UserError>,
//  EffectScope<ValidationError>,
//  UserPersistence,
//  Config.Auth
//)
context(EffectScope<ApiError>, UserPersistence, Config.Auth)
suspend fun login(input: Login): Pair<JwtToken, UserInfo> {
  val (email, password) = input.validate().bind()
  val (userId, info) = verifyPassword(email, password)
  val token = generateJwtToken(userId)
  return Pair(token, info)
}

/** Updates a user with all the provided fields, returns resulting info */
context(EffectScope<UserNotFound>, EffectScope<ValidationError>, UserPersistence)
suspend fun update(input: Update): UserInfo {
  val (userId, username, email, password, bio, image) = input.validate().bind()
  ensure(email != null || username != null || bio != null || image != null) {
    EmptyUpdate("Cannot update user with $userId with only null values")
  }
  return update(userId, email, username, password, bio, image)
}
