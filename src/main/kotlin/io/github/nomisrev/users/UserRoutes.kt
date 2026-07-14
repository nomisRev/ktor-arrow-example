@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.users

import arrow.core.raise.context.Raise
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.accumulating
import arrow.core.raise.context.withError
import io.github.nomisrev.Api
import io.github.nomisrev.Email
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.Password
import io.github.nomisrev.Username
import io.github.nomisrev.auth.JwtConfig
import io.github.nomisrev.auth.JwtContext
import io.github.nomisrev.auth.authenticateWith
import io.github.nomisrev.auth.principal
import io.github.nomisrev.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import opensavvy.spine.server.respond

@Serializable data class UserWrapper<T : Any>(val user: T)

@Serializable
data class NewUser(val username: String, val email: String, val password: String) {
    context(_: Raise<IncorrectInput>)
    fun toRegisterUser() =
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
    fun toUpdate(userId: UserId) =
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
data class User(
    val email: String,
    val token: String,
    val username: String,
    val bio: String?,
    val image: String?,
)

@Serializable
data class LoginUser(val email: String, val password: String) {
    context(_: Raise<IncorrectInput>)
    fun toLogin() =
        withError(::IncorrectInput) {
            accumulate {
                val email by accumulating { Email(email) }
                val password by accumulating { Password(password) }
                Login(email, password)
            }
        }
}

fun Route.userRoutes(userService: UserService, jwtService: JwtConfig<JwtContext>) {
    route(Api.Users.register) {
        val register = body.user.toRegisterUser()
        val token = userService.register(register)
        respond(
            UserWrapper(register.toUser(token)),
            HttpStatusCode.Created,
        )
    }

    route(Api.Users.Login.authenticate) {
        val login = body.user.toLogin()
        val (token, info) = userService.login(login)
        respond(UserWrapper(login.toUser(token, info)))
    }

    authenticateWith(jwtService) {
        route(Api.CurrentUser.get) {
            val info = userService.getUser(call.principal.userId)
            respond(
                UserWrapper(
                    User(
                        info.email,
                        call.principal.token.value,
                        info.username,
                        info.bio,
                        info.image,
                    )
                )
            )
        }

        route(Api.CurrentUser.update) {
            val update = body.user.toUpdate(call.principal.userId)
            val info = userService.update(update)
            respond(
                UserWrapper(
                    User(
                        info.email,
                        call.principal.token.value,
                        info.username,
                        info.bio,
                        info.image,
                    )
                )
            )
        }
    }
}
