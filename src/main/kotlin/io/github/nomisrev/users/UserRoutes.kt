@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.users

import io.github.nomisrev.Api
import io.github.nomisrev.auth.JwtService
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import opensavvy.spine.server.respond

@Serializable data class UserWrapper<T : Any>(val user: T)

@Serializable data class NewUser(val username: String, val email: String, val password: String)

@Serializable
data class UpdateUser(
    val email: String? = null,
    val username: String? = null,
    val password: String? = null,
    val bio: String? = null,
    val image: String? = null,
)

@Serializable
data class User(
    val email: String,
    val token: String,
    val username: String,
    val bio: String,
    val image: String,
)

@Serializable data class LoginUser(val email: String, val password: String)

fun Route.userRoutes(userService: UserService, jwtService: JwtService) {
    route(Api.Users.register) {
        val (username, email, password) = body.user
        val token = userService.register(RegisterUser(username, email, password))
        respond(UserWrapper(User(email, token.value, username, "", "")), HttpStatusCode.Created)
    }

    route(Api.Users.Login.authenticate) {
        val (email, password) = body.user
        val (token, info) = userService.login(Login(email, password))
        respond(UserWrapper(User(email, token.value, info.username, info.bio, info.image)))
    }

    route(Api.CurrentUser.get) {
        jwtAuth(jwtService) { (token, userId) ->
            val info = userService.getUser(userId)
            respond(UserWrapper(User(info.email, token.value, info.username, info.bio, info.image)))
        }
    }

    route(Api.CurrentUser.update) {
        jwtAuth(jwtService) { (token, userId) ->
            val (email, username, password, bio, image) = body.user
            val info = userService.update(Update(userId, username, email, password, bio, image))
            respond(UserWrapper(User(info.email, token.value, info.username, info.bio, info.image)))
        }
    }
}
