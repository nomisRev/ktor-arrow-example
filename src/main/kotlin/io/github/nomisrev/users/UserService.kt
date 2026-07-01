package io.github.nomisrev.users

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import io.github.nomisrev.DomainError
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.auth.JwtService
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.validate

data class RegisterUser(val username: String, val email: String, val password: String)

data class Update(
    val userId: UserId,
    val username: String?,
    val email: String?,
    val password: String?,
    val bio: String?,
    val image: String?,
)

data class Login(val email: String, val password: String)

data class UserInfo(val email: String, val username: String, val bio: String, val image: String)

class UserService(
    private val repo: UserPersistence,
    private val jwtService: JwtService,
) {
    context(_: Raise<DomainError>)
    fun register(input: RegisterUser): JwtToken {
        val (username, email, password) = input.validate()
        val userId = repo.insert(username, email, password)
        return jwtService.generateJwtToken(userId)
    }

    context(_: Raise<DomainError>)
    fun update(input: Update): UserInfo {
        val (userId, username, email, password, bio, image) = input.validate()
        ensure(email != null || username != null || bio != null || image != null) {
            EmptyUpdate("Cannot update user with $userId with only null values")
        }
        return repo.update(userId, email, username, password, bio, image)
    }

    context(_: Raise<DomainError>)
    fun login(input: Login): Pair<JwtToken, UserInfo> {
        val (email, password) = input.validate()
        val (userId, info) = repo.verifyPassword(email, password)
        val token = jwtService.generateJwtToken(userId)
        return Pair(token, info)
    }

    context(_: Raise<UserNotFound>)
    fun getUser(userId: UserId): UserInfo = repo.select(userId)

    context(_: Raise<UserNotFound>)
    fun getUser(username: String): UserInfo = repo.select(username)
}
