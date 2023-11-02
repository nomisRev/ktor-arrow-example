package io.github.nomisrev.routes

import io.github.nomisrev.withServer
import io.github.nomisrev.registerUser
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class ProfileRouteSpec :
  StringSpec({
    val validUsername = "username"
    val validUsernameFollowed = "username2"

    "Can unfollow profile" {
      withServer {
        val token = registerUser(validUsername)
        registerUser(validUsernameFollowed)

        val response =
          delete(ProfilesResource.Follow(username = validUsernameFollowed)) {
            bearerAuth(token.value)
          }

        response.status shouldBe HttpStatusCode.OK
        with(response.body<ProfileWrapper<Profile>>().profile) {
          username shouldBe validUsernameFollowed
          bio shouldBe ""
          image shouldBe ""
          following shouldBe false
        }
      }
    }

    "Needs token to unfollow" {
      withServer {
        val response = delete(ProfilesResource.Follow(username = validUsernameFollowed))

        response.status shouldBe HttpStatusCode.Unauthorized
      }
    }

    "Username invalid to unfollow" {
      withServer {
        val token = registerUser(validUsername)

        val response =
          delete(ProfilesResource.Follow(username = validUsernameFollowed)) {
            bearerAuth(token.value)
          }

        response.status shouldBe HttpStatusCode.UnprocessableEntity
      }
    }

    "Get profile with no following" {
      withServer {
        registerUser(validUsername)
        val response =
          get(ProfilesResource.Username(username = validUsername)) {
            contentType(ContentType.Application.Json)
          }

        response.status shouldBe HttpStatusCode.OK
        with(response.body<Profile>()) {
          username shouldBe validUsername
          bio shouldBe ""
          image shouldBe ""
          following shouldBe false
        }
      }
    }

    "Get profile invalid username" {
      withServer {
        val response =
          get(ProfilesResource.Username(username = validUsername)) {
            contentType(ContentType.Application.Json)
          }

        response.status shouldBe HttpStatusCode.UnprocessableEntity
        response.body<GenericErrorModel>().errors.body shouldBe
          listOf("User with username=$validUsername not found")
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
