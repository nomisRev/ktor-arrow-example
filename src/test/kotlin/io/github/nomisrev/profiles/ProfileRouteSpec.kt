package io.github.nomisrev.profiles

import de.infix.testBalloon.framework.core.testSuite
import io.github.nomisrev.Api
import io.github.nomisrev.Api.Profiles
import io.github.nomisrev.Api.Profiles.Username
import io.github.nomisrev.Api.Profiles.Username.Follow
import io.github.nomisrev.Api.Profiles.Username.Follow.add
import io.github.nomisrev.Api.Profiles.Username.Follow.remove
import io.github.nomisrev.Api.Profiles.Username.get
import io.github.nomisrev.GenericErrorModel
import io.github.nomisrev.client
import io.github.nomisrev.registerUser
import io.github.nomisrev.testServer
import io.github.nomisrev.tokenAuth
import io.github.nomisrev.userFixture
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.ResolvedResource
import opensavvy.spine.api.div
import opensavvy.spine.api.invoke
import opensavvy.spine.client.bodyOrThrow
import opensavvy.spine.client.request

@Suppress("RETURN_VALUE_NOT_USED_COERCION")
val ProfileRouteSuite by testSuite {
    testServer("can follow a profile") {
        val (token) = registerUser()
        val followed = userFixture()
        registerUser(followed)

        val response = client.request(Api / Profiles / followed.username / Follow / add) {
            tokenAuth(token.value)
        }

        assert(response.httpResponse.status == HttpStatusCode.OK)
        with(response.bodyOrThrow().profile) {
            assert(username == followed.username.value)
            assert(bio == "")
            assert(image == "")
            assert(following)
        }
    }

    testServer("can unfollow a profile") {
        val (token) = registerUser()
        val followed = userFixture()
        registerUser(followed)

        val response = client.request(Api / Profiles / followed.username / Follow / remove) {
            tokenAuth(token.value)
        }

        assert(response.httpResponse.status == HttpStatusCode.OK)
        with(response.bodyOrThrow().profile) {
            assert(username == followed.username.value)
            assert(bio == "")
            assert(image == "")
            assert(!following)
        }
    }

    testServer("needs token to follow") {
        val response = client.request(Api / Profiles / userFixture().username / Follow / add)
        assert(response.httpResponse.status == HttpStatusCode.Unauthorized)
    }

    testServer("needs token to unfollow") {
        val response = client.request(Api / Profiles / userFixture().username / Follow / remove)
        assert(response.httpResponse.status == HttpStatusCode.Unauthorized)
    }

    testServer("username invalid to follow") {
        val (token) = registerUser()

        val response = client.request(Api / Profiles / userFixture().username / Follow / add) {
            tokenAuth(token.value)
        }

        assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
    }

    testServer("username invalid to unfollow") {
        val (token) = registerUser()

        val response = client.request(Api / Profiles / userFixture().username / Follow / remove) {
            tokenAuth(token.value)
        }

        assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
    }

    testServer("get profile with no following") {
        val (user) = registerUser()

        val response = client.request(Api / Profiles / user.username / get)

        assert(response.httpResponse.status == HttpStatusCode.OK)
        with(response.bodyOrThrow().profile) {
            assert(username == user.username.value)
            assert(bio == "")
            assert(image == "")
            assert(!following)
        }
    }

    testServer("get profile shows following for current viewer") {
        val (token) = registerUser()
        val followed = userFixture()
        registerUser(followed)

        client.request(Api / Profiles / followed.username / Follow / add) {
            tokenAuth(token.value)
        }

        val response = client.request(Api / Profiles / followed.username / get) {
            tokenAuth(token.value)
        }

        assert(response.httpResponse.status == HttpStatusCode.OK)
        with(response.bodyOrThrow().profile) {
            assert(username == followed.username.value)
            assert(following)
        }
    }

    testServer("get profile follow state is viewer specific") {
        val follower = registerUser()
        val viewer = registerUser()
        val followed = registerUser()

        client.request(Api / Profiles / followed.user.username / Follow / add) {
            tokenAuth(follower.token.value)
        }

        val response = client.request(Api / Profiles / followed.user.username / get) {
            tokenAuth(viewer.token.value)
        }

        assert(response.httpResponse.status == HttpStatusCode.OK)
        with(response.bodyOrThrow().profile) {
            assert(username == followed.user.username.value)
            assert(!following)
        }
    }

    testServer("get profile invalid username") {
        val invalidUsername = userFixture().username

        val response = client.request(Api / Profiles / invalidUsername / get)

        assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
        assert(
            response.httpResponse.body<GenericErrorModel>().errors.body ==
            ["User with username=$invalidUsername not found"],
        )
    }

    testServer("get profile by username blank username") {
        val response = client.request(Api / Profiles / Username("%20") / get)

        assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
        assert(
            response.httpResponse.body<GenericErrorModel>().errors.body ==
            ["username: Cannot be blank, is too short (minimum is 1 characters)"],
        )
    }
}

operator fun ResolvedResource<Profiles>.div(username: io.github.nomisrev.users.Username) =
    div(Username(username.value))
