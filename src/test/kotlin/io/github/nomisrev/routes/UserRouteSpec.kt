package io.github.nomisrev.routes

import io.github.nomisrev.service.RegisterUser
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

class UserRouteSpec : StringSpec({

  val validUsername = "username"
  val validEmail = "valid@domain.com"
  val validPw = "123456789"

  "Can register user" {
    withService {
      val response = post("/users") {
        contentType(ContentType.Application.Json)
        setBody(UserWrapper(NewUser(validUsername, validEmail, validPw)))
      }

      response.status shouldBe HttpStatusCode.Created
      assertSoftly {
        val user = response.body<UserWrapper<User>>().user
        user.username shouldBe validUsername
        user.email shouldBe validEmail
        user.bio shouldBe ""
        user.image shouldBe ""
      }
    }
  }

  "Can log in a registered user" {
    withService { dependencies ->
      dependencies.userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()

      val response = post("/users/login") {
        contentType(ContentType.Application.Json)
        setBody(UserWrapper(LoginUser(validEmail, validPw)))
      }

      response.status shouldBe HttpStatusCode.OK
      assertSoftly {
        val user = response.body<UserWrapper<User>>().user
        user.username shouldBe validUsername
        user.email shouldBe validEmail
        user.bio shouldBe ""
        user.image shouldBe ""
      }
    }
  }

  "Can get current user" {
    withService { dependencies ->
      val token =
        dependencies.userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()

      val response = get("/user") { bearerAuth(token.value) }

      response.status shouldBe HttpStatusCode.OK
      assertSoftly {
        val user = response.body<UserWrapper<User>>().user
        user.username shouldBe validUsername
        user.email shouldBe validEmail
        user.token shouldBe token.value
        user.bio shouldBe ""
        user.image shouldBe ""
      }
    }
  }

  "Update user" {
    withService { dependencies ->
      val token =
        dependencies.userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()
      val newUsername = "newUsername"

      val response = put("/user") {
        bearerAuth(token.value)
        contentType(ContentType.Application.Json)
        setBody(UserWrapper(UpdateUser(username = newUsername)))
      }

      response.status shouldBe HttpStatusCode.OK
      assertSoftly {
        val user = response.body<UserWrapper<User>>().user
        user.username shouldBe newUsername
        user.email shouldBe validEmail
        user.token shouldBe token.value
        user.bio shouldBe ""
        user.image shouldBe ""
      }
    }
  }

  "Update user invalid email" {
    withService { dependencies ->
      val token =
        dependencies.userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()
      val inalidEmail = "invalidEmail"

      val response = put("/user") {
        bearerAuth(token.value)
        contentType(ContentType.Application.Json)
        setBody(UserWrapper(UpdateUser(email = inalidEmail)))
      }

      response.status shouldBe HttpStatusCode.UnprocessableEntity
      response.body<GenericErrorModel>().errors.body shouldBe
        listOf("email: 'invalidEmail' is invalid email")
    }
  }
})
