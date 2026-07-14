package io.github.nomisrev.auth

import de.infix.testBalloon.framework.core.testSuite
import io.github.nomisrev.Api
import io.github.nomisrev.Api.CurrentUser
import io.github.nomisrev.Api.CurrentUser.get
import io.github.nomisrev.Api.Users
import io.github.nomisrev.Api.Users.register
import io.github.nomisrev.client
import io.github.nomisrev.testServer
import io.github.nomisrev.tokenAuth
import io.github.nomisrev.userFixture
import io.github.nomisrev.users.User
import io.github.nomisrev.users.UserWrapper
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.div
import opensavvy.spine.client.request

val JwtServiceSuite by testSuite {
    testServer("registering a user returns a token that can authenticate future requests") {
        val user = userFixture()

        val registerResponse =
            client.request(
                Api / Users / register,
                UserWrapper(user.toNewUser()),
            )

        assert(registerResponse.httpResponse.status == HttpStatusCode.Created)

        val registeredUser = registerResponse.httpResponse.body<UserWrapper<User>>().user

        assert(registeredUser.username == user.username.value)
        assert(registeredUser.email == user.email.value)
        assert(registeredUser.bio == null)
        assert(registeredUser.image == null)
        assert(registeredUser.token.isNotBlank())

        val currentUserResponse =
            client.request(Api / CurrentUser / get) {
                tokenAuth(registeredUser.token)
            }

        assert(currentUserResponse.httpResponse.status == HttpStatusCode.OK)

        val currentUser = currentUserResponse.httpResponse.body<UserWrapper<User>>().user

        assert(currentUser.username == user.username.value)
        assert(currentUser.email == user.email.value)
        assert(currentUser.token == registeredUser.token)
        assert(currentUser.bio == "")
        assert(currentUser.image == "")
    }
}
