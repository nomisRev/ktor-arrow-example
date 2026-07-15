package io.github.nomisrev.users

import arrow.core.raise.context.Raise
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.accumulating
import arrow.core.raise.context.withError
import io.github.nomisrev.Email
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.Password
import io.github.nomisrev.Username
import kotlinx.serialization.Serializable

@Serializable data class UserWrapper<T : Any>(val user: T)

@Serializable
data class NewUser(val username: String, val email: String, val password: String) {
    context(_: Raise<IncorrectInput>)
    fun toRegisterUser(): RegisterUser =
        withError(::IncorrectInput) {
            accumulate {
                val username by accumulating { Username(username) }
                val email by accumulating { Email(email) }
                val password by accumulating { Password(password) }
                RegisterUser(username, email, password)
            }
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
    fun toUpdate(userId: UserId): Update =
        withError(::IncorrectInput) {
            accumulate {
                val username by accumulating { username?.let { Username(it) } }
                val email by accumulating { email?.let { Email(it) } }
                val password by accumulating { password?.let { Password(it) } }
                Update(userId, username, email, password, bio, image)
            }
        }
}

@Serializable
data class LoginUser(val email: String, val password: String) {
    context(_: Raise<IncorrectInput>)
    fun toLogin(): Login =
        withError(::IncorrectInput) {
            accumulate {
                val email by accumulating { Email(email) }
                val password by accumulating { Password(password) }
                Login(email, password)
            }
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
