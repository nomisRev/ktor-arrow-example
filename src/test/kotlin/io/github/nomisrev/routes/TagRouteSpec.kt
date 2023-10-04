package io.github.nomisrev.routes

import io.github.nomisrev.withServer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType


class TagRouteSpec :
  StringSpec({
    "can get all tags" {
      withServer {
        val response = get("/tags") { contentType(ContentType.Application.Json) }

        response.status shouldBe HttpStatusCode.OK
        response.body<TagsResponse>().tags shouldHaveSize 0
      }
    }
  })
