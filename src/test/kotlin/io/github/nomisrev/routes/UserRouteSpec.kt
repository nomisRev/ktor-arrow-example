package io.github.nomisrev.routes

import arrow.core.continuations.either
import io.github.nomisrev.with
import io.github.nomisrev.DomainError
import io.github.nomisrev.PostgreSQLContainer
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.env.hikari
import io.github.nomisrev.resource
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.service.register
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
    val env = Env().copy(dataSource = PostgreSQLContainer.config())
    val dataSource by resource(hikari(env.dataSource))
    val dependencies by resource(dependencies(env))

    val username = "username"
    val email = "valid@domain.com"
    val password = "123456789"

    afterTest { dataSource.query("TRUNCATE users CASCADE") }

    suspend fun registerUser(): JwtToken = either<DomainError, JwtToken> {
      with(dependencies.userPersistence, dependencies.env.auth) {
        register(RegisterUser(username, email, password))
      }
    }.shouldBeRight()

    "Can register user" {
      withService(dependencies) {
        val response =
          client.post("/users") {
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
      registerUser()
      withService(dependencies) {
        val response =
          client.post("/users/login") {
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
      val token = registerUser()
      withService(dependencies) {
        val response = client.get("/user") { bearerAuth(token.value) }

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
      val token = registerUser()
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
          user.email shouldBe email
          user.token shouldBe token.value
          user.bio shouldBe ""
          user.image shouldBe ""
        }
      }
    }

    "Update user invalid email" {
      val token = registerUser()
      val invalid = "invalid"
      withService(dependencies) {
        val response =
          client.put("/user") {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(UserWrapper(UpdateUser(email = invalid)))
          }

        response.status shouldBe HttpStatusCode.UnprocessableEntity
        response.body<GenericErrorModel>().errors.body shouldBe
          listOf("email: '$invalid' is invalid email")
      }
    }
  })
