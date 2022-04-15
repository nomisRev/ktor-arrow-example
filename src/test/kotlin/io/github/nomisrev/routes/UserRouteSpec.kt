package io.github.nomisrev.routes

import io.github.nomisrev.PostgreSQLContainer
import io.github.nomisrev.config.Config
import io.github.nomisrev.config.dependencies
import io.github.nomisrev.config.hikari
import io.github.nomisrev.resource
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.utils.query
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

class UserRouteSpec :
  StringSpec({
    val config = Config().copy(dataSource = PostgreSQLContainer.config())
    val dataSource by resource(hikari(config.dataSource))
    val dependencies by resource(dependencies(config))
    val userService by lazy { dependencies.userService }

    val validUsername = "username"
    val validEmail = "valid@domain.com"
    val validPw = "123456789"

    afterTest { dataSource.query("TRUNCATE users CASCADE") }

    "Can register user" {
      withService(dependencies) {
        val response =
          client.post("/users") {
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
      userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()
      withService(dependencies) {
        val response =
          client.post("/users/login") {
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
      val token =
        userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()
      withService(dependencies) {
        val response = client.get("/user") { bearerAuth(token.value) }

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
      val token =
        userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()
      val newUsername = "newUsername"
      withService(dependencies) {
        val response =
          client.put("/user") {
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
      val token =
        userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()
      val inalidEmail = "invalidEmail"
      withService(dependencies) {
        val response =
          client.put("/user") {
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
