package io.github.nomisrev.routes

import io.github.nomisrev.MIN_FEED_SIZE
import io.github.nomisrev.MIN_OFFSET
import io.github.nomisrev.withServer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.http.*

class ArticleRouteSpec :
  StringSpec({

    // User
    val kaavehUsername = "kaaveh"
    val kaavehEmail = "kaaveh@domain.com"
    val kaavehPw = "123456789"
    val simonUsername = "simon"
    val simonEmail = "simon@domain.com"
    val simonPw = "123456789"
    val johnUsername = "john"
    val johnEmail = "john@domain.com"
    val johnPw = "123456789"

    // Article
    val validTags = setOf("arrow", "ktor", "kotlin", "sqldelight")
    val validTitle = "Fake Article Arrow "
    val validDescription = "This is a fake article description."
    val validBody = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."

    "Check for empty feed" {
      withServer {
        val response =
          get(ArticleResource.Feed(offsetParam = 0)) { contentType(ContentType.Application.Json) }

        response.status shouldBe HttpStatusCode.OK
        response.body<MultipleArticlesResponse>().articles.toSet() shouldBe emptySet()
        response.body<MultipleArticlesResponse>().articlesCount shouldBe 0
      }
    }

    "ٰValidate correct both offset and limit value" {
      withServer {
        val response =
          get(ArticleResource.Feed(offsetParam = 0, limitParam = 5)) {
            contentType(ContentType.Application.Json)
          }

        response.status shouldBe HttpStatusCode.OK
        response.body<MultipleArticlesResponse>().articles.toSet() shouldBe emptySet()
        response.body<MultipleArticlesResponse>().articlesCount shouldBe 0
      }
    }

    "ٰValidate wrong offset value" {
      withServer {
        val response =
          get(ArticleResource.Feed(offsetParam = -1)) { contentType(ContentType.Application.Json) }

        response.status shouldBe HttpStatusCode.UnprocessableEntity
        response.body<GenericErrorModel>().errors.body shouldBe
          listOf("offset too small (minimum is $MIN_OFFSET)")
      }
    }

    "ٰValidate wrong limit value" {
      withServer {
        val response =
          get(ArticleResource.Feed(offsetParam = 0, limitParam = -1)) {
            contentType(ContentType.Application.Json)
          }

        response.status shouldBe HttpStatusCode.UnprocessableEntity
        response.body<GenericErrorModel>().errors.body shouldBe
          listOf("offset too small (minimum is $MIN_FEED_SIZE)")
      }
    }

    "ٰValidate wrong both limit and value" {
      withServer {
        val response =
          get(ArticleResource.Feed(offsetParam = -1, limitParam = -1)) {
            contentType(ContentType.Application.Json)
          }

        response.status shouldBe HttpStatusCode.UnprocessableEntity
        response.body<GenericErrorModel>().errors.body shouldBe
          listOf(
            "offset too small (minimum is $MIN_FEED_SIZE)",
            "offset too small (minimum is $MIN_OFFSET)",
          )
      }
    }
  })
