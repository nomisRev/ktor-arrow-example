package io.github.nomisrev.routes

import arrow.core.raise.either
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.env.Env
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.service.register
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class UserRouteSpec : StringSpec() {
  val username = "username"
  val email = "valid@domain.com"
  val password = "123456789"

  context(Env.Auth, UserPersistence)
  suspend fun registerUser(): JwtToken = either {
    register(RegisterUser(username, email, password))
  }.shouldBeRight()

  init {
    "Can register user" {
      withServer {
        val response =
          post(UsersResource()) {
            contentType(ContentType.Application.Json)
            setBody(UserWrapper(NewUser(username, email, password)))
          }

        response.status shouldBe HttpStatusCode.Created
        with(response.body<UserWrapper<User>>().user) {
          username shouldBe username
          email shouldBe email
          bio shouldBe ""
          image shouldBe ""
        }
      }
    }

    "Can log in a registered user" {
      withServer {
        registerUser()

        val response =
          post(UsersResource.Login()) {
            contentType(ContentType.Application.Json)
            setBody(UserWrapper(LoginUser(email, password)))
          }

        response.status shouldBe HttpStatusCode.OK
        with(response.body<UserWrapper<User>>().user) {
          username shouldBe username
          email shouldBe email
          bio shouldBe ""
          image shouldBe ""
        }
      }
    }

    "Can get current user" {
      withServer {
        val expected = registerUser()

        val response = get(UserResource()) { bearerAuth(expected.value) }

        response.status shouldBe HttpStatusCode.OK
        with(response.body<UserWrapper<User>>().user) {
          username shouldBe username
          email shouldBe email
          token shouldBe expected.value
          bio shouldBe ""
          image shouldBe ""
        }
      }
    }

    "Update user" {
      withServer {
        val expected = registerUser()
        val newUsername = "newUsername"

        val response =
          put(UserResource()) {
            bearerAuth(expected.value)
            contentType(ContentType.Application.Json)
            setBody(UserWrapper(UpdateUser(username = newUsername)))
          }

        response.status shouldBe HttpStatusCode.OK
        with(response.body<UserWrapper<User>>().user) {
          username shouldBe newUsername
          email shouldBe email
          token shouldBe expected.value
          bio shouldBe ""
          image shouldBe ""
        }
      }
    }

    "Update user invalid email" {
      withServer {
        val token = registerUser()
        val invalid = "invalidEmail"

        val response =
          put(UserResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(UserWrapper(UpdateUser(email = invalid)))
          }

        response.status shouldBe HttpStatusCode.UnprocessableEntity
        response.body<GenericErrorModel>().errors.body shouldBe
          listOf("email: '$invalid' is invalid email")
      }
    }
  }
}
