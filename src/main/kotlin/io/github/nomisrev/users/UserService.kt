package io.github.nomisrev.users

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import io.github.nomisrev.DomainErrors
import io.github.nomisrev.Email
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.Password
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.Username
import io.github.nomisrev.auth.JwtService
import io.github.nomisrev.auth.JwtToken

@JvmInline
value class UserId(val serial: Long)

data class RegisterUser(val username: Username, val email: Email, val password: Password)

data class Update(
    val userId: UserId,
    val username: Username?,
    val email: Email?,
    val password: Password?,
    val bio: String?,
    val image: String?,
) {
    fun isNotEmpty() = username != null || email != null || password != null || bio != null || image != null
}

data class UserInfo(val email: Email, val username: Username, val bio: String, val image: String)

data class TokenAndUserInfo(val token: JwtToken, val info: UserInfo)

data class Login(val email: Email, val password: Password)

class UserService(
    private val repo: UserPersistence,
    private val jwtService: JwtService,
) {
    context(_: DomainErrors)
    fun register(input: RegisterUser): JwtToken {
        val userId = repo.insert(input)
        return jwtService.generateJwtToken(userId)
    }

    context(_: DomainErrors)
    fun update(input: Update): UserInfo {
        ensure(input.isNotEmpty()) {
            EmptyUpdate("Cannot update user with ${input.userId} with only null values")
        }
        return repo.update(input)
    }

    context(_: DomainErrors)
    fun login(input: Login): TokenAndUserInfo {
        val (id, info) = repo.verifyPassword(input.email, input.password)
        val token = jwtService.generateJwtToken(id)
        return TokenAndUserInfo(token, info)
    }

    context(_: Raise<UserNotFound>)
    fun getUser(userId: UserId): UserInfo = repo.select(userId)
}
