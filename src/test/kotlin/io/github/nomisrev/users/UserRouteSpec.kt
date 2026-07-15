package io.github.nomisrev.users

import de.infix.testBalloon.framework.core.testSuite
import io.github.nomisrev.Api
import io.github.nomisrev.Api.CurrentUser
import io.github.nomisrev.Api.CurrentUser.get
import io.github.nomisrev.Api.CurrentUser.update
import io.github.nomisrev.Api.Users
import io.github.nomisrev.Api.Users.Login
import io.github.nomisrev.Api.Users.Login.authenticate
import io.github.nomisrev.Api.Users.register
import io.github.nomisrev.GenericErrorModel
import io.github.nomisrev.client
import io.github.nomisrev.registerUser
import io.github.nomisrev.testServer
import io.github.nomisrev.tokenAuth
import io.github.nomisrev.userFixture
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.div
import opensavvy.spine.client.request

val UserRouteSuite by testSuite {
    testServer("can register user") {
        val user = userFixture()
        val response = client.request(
            Api / Users / register,
            UserWrapper(user.toNewUser()),
        )

        assert(response.httpResponse.status == HttpStatusCode.Created)
        with(response.httpResponse.body<UserWrapper<User>>().user) {
            assert(username == user.username.value)
            assert(email == user.email.value)
            assert(bio == null)
            assert(image == null)
        }
    }

    testServer("can log in a registered user") {
        val (user) = registerUser()

        val response = client.request(
            Api / Users / Login / authenticate,
            UserWrapper(LoginUser(user.email.value, user.password.raw())),
        )

        assert(response.httpResponse.status == HttpStatusCode.OK)
        with(response.httpResponse.body<UserWrapper<User>>().user) {
            assert(username == user.username.value)
            assert(email == user.email.value)
            assert(bio == "")
            assert(image == "")
        }
    }

    testServer("can get current user") {
        val (user, token) = registerUser()

        val response = client.request(Api / CurrentUser / get) { tokenAuth(token.value) }

        assert(response.httpResponse.status == HttpStatusCode.OK)
        val body = response.httpResponse.body<UserWrapper<User>>().user
        assert(body.username == user.username.value)
        assert(body.email == user.email.value)
        assert(body.token == token.value)
        assert(body.bio == "")
        assert(body.image == "")
    }

    testServer("can update user") {
        val (user, token) = registerUser()
        val newUsername = "new-${user.username.value}"

        val response = client.request(
            Api / CurrentUser / update,
            UserWrapper(UpdateUser(username = newUsername)),
        ) {
            tokenAuth(token.value)
        }

        assert(response.httpResponse.status == HttpStatusCode.OK)
        val body = response.httpResponse.body<UserWrapper<User>>().user
        assert(body.username == newUsername)
        assert(body.email == user.email.value)
        assert(body.token == token.value)
        assert(body.bio == "")
        assert(body.image == "")
    }

    testServer("update user invalid email") {
        val (token) = registerUser()
        val invalidEmail = "invalidEmail"

        val response = client.request(
            Api / CurrentUser / update,
            UserWrapper(UpdateUser(email = invalidEmail)),
        ) {
            tokenAuth(token.value)
        }

        assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
        assert(
            response.httpResponse.body<GenericErrorModel>().errors.body ==
            ["email: 'invalidEmail' is invalid email"],
        )
    }
}
