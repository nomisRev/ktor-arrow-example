package io.github.nomisrev.profiles

import io.github.nomisrev.Api
import io.github.nomisrev.Api.Profiles
import io.github.nomisrev.Api.Profiles.Username
import io.github.nomisrev.Api.Profiles.Username.Follow
import io.github.nomisrev.Api.Profiles.Username.Follow.add
import io.github.nomisrev.Api.Profiles.Username.Follow.remove
import io.github.nomisrev.Api.Profiles.Username.get
import io.github.nomisrev.GenericErrorModel
import io.github.nomisrev.registerUser
import io.github.nomisrev.tokenAuth
import io.github.nomisrev.userFixture
import io.github.nomisrev.withServer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.http.*
import opensavvy.spine.api.*
import opensavvy.spine.client.bodyOrThrow
import opensavvy.spine.client.request

class ProfileRouteSpec :
    StringSpec({
        "Can follow profile" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()
                val followed = userFixture()
                dependencies.registerUser(followed)

                val response =
                    request(Api / Profiles / Username(followed.username) / Follow / add) {
                        tokenAuth(token.value)
                    }

                response.httpResponse.status shouldBe HttpStatusCode.OK
                with(response.bodyOrThrow().profile) {
                    username shouldBe followed.username
                    bio shouldBe ""
                    image shouldBe ""
                    following shouldBe true
                }
            }
        }

        "Can unfollow profile" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()
                val followed = userFixture()
                dependencies.registerUser(followed)

                val response =
                    request(Api / Profiles / Username(followed.username) / Follow / remove) {
                        tokenAuth(token.value)
                    }

                response.httpResponse.status shouldBe HttpStatusCode.OK
                with(response.bodyOrThrow().profile) {
                    username shouldBe followed.username
                    bio shouldBe ""
                    image shouldBe ""
                    following shouldBe false
                }
            }
        }

        "Needs token to follow" {
            withServer {
                val response =
                    request(Api / Profiles / Username(userFixture().username) / Follow / add)
                response.httpResponse.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "Needs token to unfollow" {
            withServer {
                val response =
                    request(Api / Profiles / Username(userFixture().username) / Follow / remove)
                response.httpResponse.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "Username invalid to follow" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()

                val response =
                    request(Api / Profiles / Username(userFixture().username) / Follow / add) {
                        tokenAuth(token.value)
                    }

                response.httpResponse.status shouldBe HttpStatusCode.UnprocessableEntity
            }
        }

        "Username invalid to unfollow" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()

                val response =
                    request(Api / Profiles / Username(userFixture().username) / Follow / remove) {
                        tokenAuth(token.value)
                    }

                response.httpResponse.status shouldBe HttpStatusCode.UnprocessableEntity
            }
        }

        "Get profile with no following" {
            withServer { dependencies ->
                val (user) = dependencies.registerUser()

                val response = request(Api / Profiles / Username(user.username) / get)

                response.httpResponse.status shouldBe HttpStatusCode.OK
                with(response.bodyOrThrow().profile) {
                    username shouldBe user.username
                    bio shouldBe ""
                    image shouldBe ""
                    following shouldBe false
                }
            }
        }

        "Get profile shows following for current viewer" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()
                val followed = userFixture()
                dependencies.registerUser(followed)

                request(Api / Profiles / Username(followed.username) / Follow / add) {
                    tokenAuth(token.value)
                }

                val response =
                    request(Api / Profiles / Username(followed.username) / get) {
                        tokenAuth(token.value)
                    }

                response.httpResponse.status shouldBe HttpStatusCode.OK
                with(response.bodyOrThrow().profile) {
                    username shouldBe followed.username
                    following shouldBe true
                }
            }
        }

        "Get profile follow state is viewer specific" {
            withServer { dependencies ->
                val follower = dependencies.registerUser()
                val viewer = dependencies.registerUser()
                val followed = dependencies.registerUser()

                request(Api / Profiles / Username(followed.user.username) / Follow / add) {
                    tokenAuth(follower.token.value)
                }

                val response =
                    request(Api / Profiles / Username(followed.user.username) / get) {
                        tokenAuth(viewer.token.value)
                    }

                response.httpResponse.status shouldBe HttpStatusCode.OK
                with(response.bodyOrThrow().profile) {
                    username shouldBe followed.user.username
                    following shouldBe false
                }
            }
        }

        "Get profile invalid username" {
            withServer {
                val invalidUsername = userFixture().username

                val response = request(Api / Profiles / Username(invalidUsername) / get)

                response.httpResponse.status shouldBe HttpStatusCode.UnprocessableEntity
                response.httpResponse.body<GenericErrorModel>().errors.body shouldBe
                    listOf("User with username=$invalidUsername not found")
            }
        }

        "Get profile by username missing username" {
            withServer {
                val response = request(Api / Profiles / Username("%20") / get)

                response.httpResponse.status shouldBe HttpStatusCode.UnprocessableEntity
                response.httpResponse.body<GenericErrorModel>().errors.body shouldBe
                    listOf("Missing username cannot be null or blank parameter in request")
            }
        }

        // TODO: report bug to Spine
        "Get profile by username missing username"
            .config(enabled = false) {
                withServer {
                    val response = request(Api / Profiles / Username(" ") / get)

                    response.httpResponse.status shouldBe HttpStatusCode.UnprocessableEntity
                    response.httpResponse.body<GenericErrorModel>().errors.body shouldBe
                        listOf("Missing username cannot be null or blank parameter in request")
                }
            }
    })
