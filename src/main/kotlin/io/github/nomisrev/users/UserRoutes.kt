package io.github.nomisrev.users

import io.github.nomisrev.Api.CurrentUser
import io.github.nomisrev.Api.Users
import io.github.nomisrev.auth.JwtConfig
import io.github.nomisrev.auth.JwtContext
import io.github.nomisrev.auth.authenticateWith
import io.github.nomisrev.auth.principal
import io.github.nomisrev.route
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import opensavvy.spine.server.respond

fun Route.userRoutes(userService: UserService, jwtService: JwtConfig<JwtContext>) {
    route(Users.register) {
        val register = body.user.toRegisterUser()
        val token = userService.register(register)
        respond(
            UserWrapper(
                User(
                    register.email.value,
                    token.value,
                    register.username.value,
                    bio = null,
                    image = null,
                )
            ),
            HttpStatusCode.Created,
        )
    }

    route(Users.Login.authenticate) {
        val login = body.user.toLogin()
        val (token, info) = userService.login(login)
        respond(
            UserWrapper(
                User(login.email.value, token.value, info.username.value, info.bio, info.image)
            )
        )
    }

    authenticateWith(jwtService) {
        route(CurrentUser.get) {
            val info = userService.getUser(call.principal.userId)
            respond(
                UserWrapper(
                    User(
                        info.email.value,
                        call.principal.token.value,
                        info.username.value,
                        info.bio,
                        info.image,
                    )
                )
            )
        }

        route(CurrentUser.update) {
            val info = userService.update(body.user.toUpdate(call.principal.userId))
            respond(
                UserWrapper(
                    User(
                        info.email.value,
                        call.principal.token.value,
                        info.username.value,
                        info.bio,
                        info.image,
                    )
                )
            )
        }
    }
}
