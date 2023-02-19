package io.github.nomisrev.routes

import arrow.core.continuations.either
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.env.Env
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.service.register
import io.github.nomisrev.withService
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
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
      withService {
        val response =
          post("/users") {
            contentType(ContentType.Application.Json)
            setBody(UserWrapper(NewUser(username, email, password)))
          }

        response.status shouldBe HttpStatusCode.Created
        assertSoftly {
          val user = response.body<UserWrapper<User>>().user
          user.username shouldBe username
          user.email shouldBe email
          user.bio shouldBe ""
          user.image shouldBe ""
        }
      }
    }

    "Can log in a registered user" {
      withService {
        registerUser()

        val response =
          post("/users/login") {
            contentType(ContentType.Application.Json)
            setBody(UserWrapper(LoginUser(email, password)))
          }

        response.status shouldBe HttpStatusCode.OK
        assertSoftly {
          val user = response.body<UserWrapper<User>>().user
          user.username shouldBe username
          user.email shouldBe email
          user.bio shouldBe ""
          user.image shouldBe ""
        }
      }
    }

    "Can get current user" {
      withService {
        val token = registerUser()

        val response = get("/user") { bearerAuth(token.value) }

        response.status shouldBe HttpStatusCode.OK
        assertSoftly {
          val user = response.body<UserWrapper<User>>().user
          user.username shouldBe username
          user.email shouldBe email
          user.token shouldBe token.value
          user.bio shouldBe ""
          user.image shouldBe ""
        }
      }
    }

    "Update user" {
      withService {
        val token = registerUser()
        val newUsername = "newUsername"

        val response =
          put("/user") {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(UserWrapper(UpdateUser(username = newUsername)))
          }

        response.status shouldBe HttpStatusCode.OK
        assertSoftly {
          val user = response.body<UserWrapper<User>>().user
          user.username shouldBe newUsername
          user.email shouldBe email
          user.token shouldBe token.value
          user.bio shouldBe ""
          user.image shouldBe ""
        }
      }
    }

    "Update user invalid email" {
      withService {
        val token = registerUser()
        val invalid = "invalidEmail"

        val response =
          put("/user") {
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
