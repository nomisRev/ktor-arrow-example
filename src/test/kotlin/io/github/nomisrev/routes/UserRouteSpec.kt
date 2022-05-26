package io.github.nomisrev.routes

import io.github.nomisrev.PostgreSQLContainer
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.resource
import io.github.nomisrev.withService
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class UserRouteSpec :
  StringSpec({
    val env = Env().copy(dataSource = PostgreSQLContainer.config())
    val dependencies by resource(dependencies(env))

    val username = "username"
    val email = "valid@domain.com"
    val password = "123456789"

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
  })
