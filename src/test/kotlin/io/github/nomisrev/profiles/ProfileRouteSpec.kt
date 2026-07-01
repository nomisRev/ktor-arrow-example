package io.github.nomisrev.profiles

import arrow.core.raise.either
import io.github.nomisrev.Api
import io.github.nomisrev.Api.Profiles
import io.github.nomisrev.Api.Profiles.Username
import io.github.nomisrev.Api.Profiles.Username.Follow
import io.github.nomisrev.Api.Profiles.Username.Follow.add
import io.github.nomisrev.Api.Profiles.Username.Follow.remove
import io.github.nomisrev.Api.Profiles.Username.get
import io.github.nomisrev.GenericErrorModel
import io.github.nomisrev.userFixture
import io.github.nomisrev.users.RegisterUser
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.bearerAuth
import io.ktor.http.*
import opensavvy.spine.api.*
import opensavvy.spine.client.bodyOrThrow
import opensavvy.spine.client.request

class ProfileRouteSpec :
    StringSpec({
        "Can follow profile" {
            withServer { dependencies ->
                val follower = userFixture()
                val followed = userFixture()

                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(follower.username, follower.email, follower.password)
                            )
                        }
                        .shouldBeRight()

                either {
                        dependencies.userService.register(
                            RegisterUser(followed.username, followed.email, followed.password)
                        )
                    }
                    .shouldBeRight()

                val response =
                    request(Api / Profiles / Username(followed.username) / Follow / add) {
                        bearerAuth(token.value)
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
                val follower = userFixture()
                val followed = userFixture()

                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(follower.username, follower.email, follower.password)
                            )
                        }
                        .shouldBeRight()

                either {
                        dependencies.userService.register(
                            RegisterUser(followed.username, followed.email, followed.password)
                        )
                    }
                    .shouldBeRight()

                val response =
                    request(Api / Profiles / Username(followed.username) / Follow / remove) {
                        bearerAuth(token.value)
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
                val follower = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(follower.username, follower.email, follower.password)
                            )
                        }
                        .shouldBeRight()

                val response =
                    request(Api / Profiles / Username(userFixture().username) / Follow / add) {
                        bearerAuth(token.value)
                    }

                response.httpResponse.status shouldBe HttpStatusCode.UnprocessableEntity
            }
        }

        "Username invalid to unfollow" {
            withServer { dependencies ->
                val follower = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(follower.username, follower.email, follower.password)
                            )
                        }
                        .shouldBeRight()

                val response =
                    request(Api / Profiles / Username(userFixture().username) / Follow / remove) {
                        bearerAuth(token.value)
                    }

                response.httpResponse.status shouldBe HttpStatusCode.UnprocessableEntity
            }
        }

        "Get profile with no following" {
            withServer { dependencies ->
                val user = userFixture()

                either {
                        dependencies.userService.register(
                            RegisterUser(user.username, user.email, user.password)
                        )
                    }
                    .shouldBeRight()

                val response =
                    request(Api / Profiles / Username(user.username) / get)

                response.httpResponse.status shouldBe HttpStatusCode.OK
                with(response.bodyOrThrow()) {
                    username shouldBe user.username
                    bio shouldBe ""
                    image shouldBe ""
                    following shouldBe false
                }
            }
        }

        "Get profile invalid username" {
            withServer {
                val invalidUsername = userFixture().username

                val response =
                    request(Api / Profiles / Username(invalidUsername) / get)

                response.httpResponse.status shouldBe HttpStatusCode.UnprocessableEntity
                response.httpResponse.body<GenericErrorModel>().errors.body shouldBe
                    listOf("User with username=$invalidUsername not found")
            }
        }

        "Get profile by username missing username" {
            withServer {
                val response =
                    request(Api / Profiles / Username("%20") / get)

                response.httpResponse.status shouldBe HttpStatusCode.UnprocessableEntity
                response.httpResponse.body<GenericErrorModel>().errors.body shouldBe
                    listOf("Missing username cannot be null or blank parameter in request")
            }
        }

        // TODO: report bug to Spine
        "Get profile by username missing username"
            .config(enabled = false) {
                withServer {
                    val response =
                        request(Api / Profiles / Username(" ") / get)

                    response.httpResponse.status shouldBe HttpStatusCode.UnprocessableEntity
                    response.httpResponse.body<GenericErrorModel>().errors.body shouldBe
                        listOf("Missing username cannot be null or blank parameter in request")
                }
            }
    })
