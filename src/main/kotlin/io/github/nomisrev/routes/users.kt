package io.github.nomisrev.routes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.either
import io.github.nomisrev.IncorrectJson
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.service.JwtService
import io.github.nomisrev.service.Login
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.service.Update
import io.github.nomisrev.service.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable

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

@Resource("/users")
data class UsersResource(val parent: RootResource = RootResource) {
    @Resource("/login") data class Login(val parent: UsersResource = UsersResource())
}

@Resource("/user") data class UserResource(val parent: RootResource = RootResource)

fun Route.userRoutes(userService: UserService, jwtService: JwtService) {
    /* Registration: POST /api/users */
    post<UsersResource> {
        either {
                val (username, email, password) = receiveCatching<UserWrapper<NewUser>>().user
                val token = userService.register(RegisterUser(username, email, password))
                UserWrapper(User(email, token.value, username, "", ""))
            }
            .respond(HttpStatusCode.Created)
    }

    post<UsersResource.Login> {
        either {
                val (email, password) = receiveCatching<UserWrapper<LoginUser>>().user
                val (token, info) = userService.login(Login(email, password))
                UserWrapper(User(email, token.value, info.username, info.bio, info.image))
            }
            .respond(HttpStatusCode.OK)
    }

    /* Get Current User: GET /api/user */
    get<UserResource> {
        jwtAuth(jwtService) { (token, userId) ->
            either {
                    val info = userService.getUser(userId)
                    UserWrapper(User(info.email, token.value, info.username, info.bio, info.image))
                }
                .respond(HttpStatusCode.OK)
        }
    }

    /* Update current user: PUT /api/user */
    put<UserResource> {
        jwtAuth(jwtService) { (token, userId) ->
            either {
                    val (email, username, password, bio, image) =
                        receiveCatching<UserWrapper<UpdateUser>>().user
                    val info =
                        userService.update(Update(userId, username, email, password, bio, image))
                    UserWrapper(User(info.email, token.value, info.username, info.bio, info.image))
                }
                .respond(HttpStatusCode.OK)
        }
    }
}

// TODO improve how we receive models with validation
@OptIn(ExperimentalSerializationApi::class)
context(_: Raise<IncorrectJson>)
private suspend inline fun <reified A : Any> RoutingContext.receiveCatching(): A {
    return try {
        call.receive()
    } catch (e: MissingFieldException) {
        raise(IncorrectJson(e))
    }
}
