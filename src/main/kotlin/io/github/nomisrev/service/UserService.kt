package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.continuations.either
import io.github.nomisrev.DomainError
import io.github.nomisrev.persistence.UserId
import io.github.nomisrev.persistence.UserPersistence
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
  suspend fun register(input: RegisterUser): Either<DomainError, JwtToken>
}

fun userService(repo: UserPersistence, jwtService: JwtService) = object : UserService {
  override suspend fun register(input: RegisterUser): Either<DomainError, JwtToken> =
    either {
      val (username, email, password) = input.validate().bind()
      val userId = repo.insert(username, email, password).bind()
      jwtService.generateJwtToken(userId).bind()
    }
}
