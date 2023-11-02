package io.github.nomisrev.routes

import arrow.core.raise.either
import io.github.nomisrev.registerUser
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.service.CreateArticle
import io.github.nomisrev.service.createArticle
import io.github.nomisrev.service.shouldHaveUserId
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode

class ArticlesRouteSpec :
  StringSpec({
    val validUsername = "username2"
    val validTags = setOf("arrow", "kotlin", "ktor", "sqldelight")
    val validTitle = "Fake Article Arrow "
    val validDescription = "This is a fake article description."
    val validBody = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."

    "Article by slug not found" {
      withServer {
        val response = get(ArticlesResource.Slug(slug = "slug"))

        response.status shouldBe HttpStatusCode.UnprocessableEntity
        response.body<GenericErrorModel>().errors.body shouldBe
          listOf("Article by slug slug not found")
      }
    }

    "Can get an article by slug" {
      withServer {
        val userId = registerUser(validUsername).shouldHaveUserId()

        val article = either {
          createArticle(
            CreateArticle(UserId(userId), validTitle, validDescription, validBody, validTags)
          )
        }.shouldBeRight()

        val response = get(ArticlesResource.Slug(slug = article.slug))

        response.status shouldBe HttpStatusCode.OK
        response.body<SingleArticleResponse>().article shouldBe article
      }
    }
  })
