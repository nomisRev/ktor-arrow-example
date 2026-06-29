package io.github.nomisrev.routes

import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.userFixture
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class ProfileRouteSpec :
    StringSpec({


        "Can follow profile" {
            withServer { dependencies ->
                val follower = userFixture()
                val followed = userFixture()
                val token =
                    dependencies.userService
                        .register(
                            RegisterUser(follower.username, follower.email, follower.password)
                        )
                        .shouldBeRight()
                dependencies.userService
                    .register(RegisterUser(followed.username, followed.email, followed.password))
                    .shouldBeRight()

                val response =
                    post(ProfilesResource.Follow(username = followed.username)) {
                        bearerAuth(token.value)
                    }

                response.status shouldBe HttpStatusCode.OK
                with(response.body<ProfileWrapper<Profile>>().profile) {
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
                    dependencies.userService
                        .register(
                            RegisterUser(follower.username, follower.email, follower.password)
                        )
                        .shouldBeRight()
                dependencies.userService
                    .register(RegisterUser(followed.username, followed.email, followed.password))
                    .shouldBeRight()

                val response =
                    delete(ProfilesResource.Follow(username = followed.username)) {
                        bearerAuth(token.value)
                    }

                response.status shouldBe HttpStatusCode.OK
                with(response.body<ProfileWrapper<Profile>>().profile) {
                    username shouldBe followed.username
                    bio shouldBe ""
                    image shouldBe ""
                    following shouldBe false
                }
            }
        }

        "Needs token to follow" {
            withServer {
                val response = post(ProfilesResource.Follow(username = userFixture().username))

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "Needs token to unfollow" {
            withServer {
                val response = delete(ProfilesResource.Follow(username = userFixture().username))

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "Username invalid to follow" {
            withServer { dependencies ->
                val follower = userFixture()
                val token =
                    dependencies.userService
                        .register(
                            RegisterUser(follower.username, follower.email, follower.password)
                        )
                        .shouldBeRight()

                val response =
                    post(ProfilesResource.Follow(username = userFixture().username)) {
                        bearerAuth(token.value)
                    }

                response.status shouldBe HttpStatusCode.UnprocessableEntity
            }
        }

        "Username invalid to unfollow" {
            withServer { dependencies ->
                val follower = userFixture()
                val token =
                    dependencies.userService
                        .register(
                            RegisterUser(follower.username, follower.email, follower.password)
                        )
                        .shouldBeRight()

                val response =
                    delete(ProfilesResource.Follow(username = userFixture().username)) {
                        bearerAuth(token.value)
                    }

                response.status shouldBe HttpStatusCode.UnprocessableEntity
            }
        }

        "Get profile with no following" {
            withServer { dependencies ->
                val user = userFixture()
                dependencies.userService
                    .register(RegisterUser(user.username, user.email, user.password))
                    .shouldBeRight()
                val response =
                    get(ProfilesResource.Username(username = user.username)) {
                        contentType(ContentType.Application.Json)
                    }

                response.status shouldBe HttpStatusCode.OK
                with(response.body<Profile>()) {
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
                    get(ProfilesResource.Username(username = invalidUsername)) {
                        contentType(ContentType.Application.Json)
                    }

                response.status shouldBe HttpStatusCode.UnprocessableEntity
                response.body<GenericErrorModel>().errors.body shouldBe
                    listOf("User with username=$invalidUsername not found")
            }
        }

        "Get profile by username missing username" {
            withServer {
                val response =
                    get(ProfilesResource.Username(username = " ")) {
                        contentType(ContentType.Application.Json)
                    }

                response.status shouldBe HttpStatusCode.UnprocessableEntity
                response.body<GenericErrorModel>().errors.body shouldBe
                    listOf("Missing username parameter in request")
            }
        }
    })
