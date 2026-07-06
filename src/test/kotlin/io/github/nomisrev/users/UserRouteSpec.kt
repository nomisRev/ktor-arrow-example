package io.github.nomisrev.users

import io.github.nomisrev.Api
import io.github.nomisrev.Api.CurrentUser
import io.github.nomisrev.Api.CurrentUser.get
import io.github.nomisrev.Api.CurrentUser.update
import io.github.nomisrev.Api.Users
import io.github.nomisrev.Api.Users.Login
import io.github.nomisrev.Api.Users.Login.authenticate
import io.github.nomisrev.Api.Users.register
import io.github.nomisrev.GenericErrorModel
import io.github.nomisrev.registerUser
import io.github.nomisrev.tokenAuth
import io.github.nomisrev.userFixture
import io.github.nomisrev.withServer
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.div
import opensavvy.spine.client.request

class UserRouteSpec :
    StringSpec({
        "Can register user" {
            withServer { _ ->
                val user = userFixture()
                val response =
                    request(
                        Api / Users / register,
                        UserWrapper(NewUser(user.username, user.email, user.password)),
                    )

                assert(response.httpResponse.status == HttpStatusCode.Created)
                with(response.httpResponse.body<UserWrapper<User>>().user) {
                    assert(username == user.username)
                    assert(email == user.email)
                    assert(bio == "")
                    assert(image == "")
                }
            }
        }

        "Can log in a registered user" {
            withServer { dependencies ->
                val (user) = dependencies.registerUser()

                val response =
                    request(
                        Api / Users / Login / authenticate,
                        UserWrapper(LoginUser(user.email, user.password)),
                    )

                assert(response.httpResponse.status == HttpStatusCode.OK)
                with(response.httpResponse.body<UserWrapper<User>>().user) {
                    assert(username == user.username)
                    assert(email == user.email)
                    assert(bio == "")
                    assert(image == "")
                }
            }
        }

        "Can get current user" {
            withServer { dependencies ->
                val (user, token) = dependencies.registerUser()

                val response = request(Api / CurrentUser / get) { tokenAuth(token.value) }

                assert(response.httpResponse.status == HttpStatusCode.OK)
                val body = response.httpResponse.body<UserWrapper<User>>().user
                assert(body.username == user.username)
                assert(body.email == user.email)
                assert(body.token == token.value)
                assert(body.bio == "")
                assert(body.image == "")
            }
        }

        "Update user" {
            withServer { dependencies ->
                val (user, token) = dependencies.registerUser()
                val newUsername = "new-${user.username}"

                val response =
                    request(
                        Api / CurrentUser / update,
                        UserWrapper(UpdateUser(username = newUsername)),
                    ) {
                        tokenAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.OK)
                val body = response.httpResponse.body<UserWrapper<User>>().user
                assert(body.username == newUsername)
                assert(body.email == user.email)
                assert(body.token == token.value)
                assert(body.bio == "")
                assert(body.image == "")
            }
        }

        "Update user invalid email" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()
                val invalidEmail = "invalidEmail"

                val response =
                    request(
                        Api / CurrentUser / update,
                        UserWrapper(UpdateUser(email = invalidEmail)),
                    ) {
                        tokenAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.httpResponse.body<GenericErrorModel>().errors.body ==
                        listOf("email: 'invalidEmail' is invalid email")
                )
            }
        }
    })
