package io.github.nomisrev.users

import arrow.core.raise.context.Raise
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.accumulating
import io.github.nomisrev.validate
import kotlinx.serialization.Serializable

@Serializable
data class UserWrapper<T : Any>(val user: T)

@Serializable
data class RegisterUserRequest(val username: String, val email: String, val password: String) {
    context(_: Raise<IncorrectInput>)
    fun toRegisterUser(): RegisterUser = validate(::IncorrectInput) {
        val username by accumulating { Username(username) }
        val email by accumulating { Email(email) }
        val password by accumulating { Password(password) }
        RegisterUser(username, email, password)
    }
}

@Serializable
data class UpdateUser(
    val email: String? = null,
    val username: String? = null,
    val password: String? = null,
    val bio: String? = null,
    val image: String? = null,
) {
    context(_: Raise<IncorrectInput>)
    fun toUpdate(userId: UserId): Update = validate(::IncorrectInput) {
        val username by accumulating { username?.let { Username(it) } }
        val email by accumulating { email?.let { Email(it) } }
        val password by accumulating { password?.let { Password(it) } }
        Update(userId, username, email, password, bio, image)
    }
}

@Serializable
data class LoginUser(val email: String, val password: String) {
    context(_: Raise<IncorrectInput>)
    fun toLogin(): Login = validate(::IncorrectInput) {
        val email by accumulating { Email(email) }
        val password by accumulating { Password(password) }
        Login(email, password)
    }
}

@Serializable
data class User(
    val email: String,
    val token: String,
    val username: String,
    val bio: String?,
    val image: String?,
)
