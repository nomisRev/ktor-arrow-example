package io.github.nomisrev

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class ApplicationTest :
  StringSpec({
    "happy birthday - happy flow" {
      testApplication {
        application { configure() }
        val response = client.get("/happy-birthday/Santa/999")
        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe personJson("Santa", 999)
      }
    }

    "happy birthday - text for age error" {
      testApplication {
        application { configure() }
        val response = client.config { expectSuccess = false }.get("/happy-birthday/Santa/nine")

        response.status shouldBe HttpStatusCode.BadRequest
        response.bodyAsText() shouldBe "The following errors were found: nine is not a number"
      }
    }

    "happy birthday - age missing from path => NotFound" {
      testApplication {
        application { configure() }
        val response = client.config { expectSuccess = false }.get("/happy-birthday/Santa")

        response.status shouldBe HttpStatusCode.NotFound
      }
    }
  })

fun personJson(name: String, age: Int): String = "{\"age\":$age,\"name\":\"$name\"}"
