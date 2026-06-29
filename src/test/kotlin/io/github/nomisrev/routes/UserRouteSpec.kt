package io.github.nomisrev.routes

import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.userFixture
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class UserRouteSpec :
    StringSpec({


        "Can register user" {
            withServer { _ ->
                val user = userFixture()
                val response =
                    post(UsersResource()) {
                        contentType(ContentType.Application.Json)
                        setBody(UserWrapper(NewUser(user.username, user.email, user.password)))
                    }

                assert(response.status == HttpStatusCode.Created)
                with(response.body<UserWrapper<User>>().user) {
                    assert(username == user.username)
                    assert(email == user.email)
                    assert(bio == "")
                    assert(image == "")
                }
            }
        }

        "Can log in a registered user" {
            withServer { dependencies ->
                val user = userFixture()
                dependencies.userService
                    .register(RegisterUser(user.username, user.email, user.password))
                    .shouldBeRight()

                val response =
                    post(UsersResource.Login()) {
                        contentType(ContentType.Application.Json)
                        setBody(UserWrapper(LoginUser(user.email, user.password)))
                    }

                assert(response.status == HttpStatusCode.OK)
                with(response.body<UserWrapper<User>>().user) {
                    assert(username == user.username)
                    assert(email == user.email)
                    assert(bio == "")
                    assert(image == "")
                }
            }
        }

        "Can get current user" {
            withServer { dependencies ->
                val user = userFixture()
                val expected =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()

                val response = get(UserResource()) { bearerAuth(expected.value) }

                assert(response.status == HttpStatusCode.OK)
                with(response.body<UserWrapper<User>>().user) {
                    assert(username == user.username)
                    assert(email == user.email)
                    assert(token == expected.value)
                    assert(bio == "")
                    assert(image == "")
                }
            }
        }

        "Update user" {
            withServer { dependencies ->
                val user = userFixture()
                val expected =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val newUsername = "new-${user.username}"

                val response =
                    put(UserResource()) {
                        bearerAuth(expected.value)
                        contentType(ContentType.Application.Json)
                        setBody(UserWrapper(UpdateUser(username = newUsername)))
                    }

                assert(response.status == HttpStatusCode.OK)
                with(response.body<UserWrapper<User>>().user) {
                    assert(username == newUsername)
                    assert(email == user.email)
                    assert(token == expected.value)
                    assert(bio == "")
                    assert(image == "")
                }
            }
        }

        "Update user invalid email" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val invalidEmail = "invalidEmail"

                val response =
                    put(UserResource()) {
                        bearerAuth(token.value)
                        contentType(ContentType.Application.Json)
                        setBody(UserWrapper(UpdateUser(email = invalidEmail)))
                    }

                assert(response.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.body<GenericErrorModel>().errors.body ==
                        listOf("email: 'invalidEmail' is invalid email")
                )
            }
        }
    })
