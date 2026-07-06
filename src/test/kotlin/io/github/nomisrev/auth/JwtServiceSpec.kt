package io.github.nomisrev.auth

import io.github.nomisrev.Api
import io.github.nomisrev.Api.CurrentUser
import io.github.nomisrev.Api.CurrentUser.get
import io.github.nomisrev.Api.Users
import io.github.nomisrev.Api.Users.register
import io.github.nomisrev.SuspendFun
import io.github.nomisrev.tokenAuth
import io.github.nomisrev.userFixture
import io.github.nomisrev.users.NewUser
import io.github.nomisrev.users.User
import io.github.nomisrev.users.UserWrapper
import io.github.nomisrev.withServer
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.div
import opensavvy.spine.client.request

class JwtServiceSpec :
    SuspendFun({
        "registerUser -> successful auth" -
            {
                "registering a user returns a token that can authenticate future requests" {
                    withServer { _ ->
                        val user = userFixture()

                        val registerResponse =
                            request(
                                Api / Users / register,
                                UserWrapper(NewUser(user.username, user.email, user.password)),
                            )

                        assert(registerResponse.httpResponse.status == HttpStatusCode.Created)

                        val registeredUser =
                            registerResponse.httpResponse.body<UserWrapper<User>>().user

                        assert(registeredUser.username == user.username)
                        assert(registeredUser.email == user.email)
                        assert(registeredUser.bio == "")
                        assert(registeredUser.image == "")
                        assert(registeredUser.token.isNotBlank())

                        val currentUserResponse =
                            request(Api / CurrentUser / get) {
                                tokenAuth(registeredUser.token)
                            }

                        assert(currentUserResponse.httpResponse.status == HttpStatusCode.OK)

                        val currentUser =
                            currentUserResponse.httpResponse.body<UserWrapper<User>>().user

                        assert(currentUser.username == user.username)
                        assert(currentUser.email == user.email)
                        assert(currentUser.token == registeredUser.token)
                        assert(currentUser.bio == "")
                        assert(currentUser.image == "")
                    }
                }
            }
    })
