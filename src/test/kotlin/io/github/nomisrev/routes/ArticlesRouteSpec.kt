package io.github.nomisrev.routes

import arrow.core.flatMap
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.service.CreateArticle
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class ArticlesRouteSpec :
  StringSpec({
    // User
    val validUsername = "username2"
    val validEmail = "valid2@domain.com"
    val validPw = "123456789"
    // Article
    val validTags = setOf("arrow", "kotlin", "ktor", "sqldelight")
    val validTitle = "Fake Article Arrow "
    val validDescription = "This is a fake article description."
    val validBody = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."

    "Article by slug not found" {
      withServer {
        val response = get("articles/slug")

        response.status shouldBe HttpStatusCode.UnprocessableEntity
        response.body<GenericErrorModel>().errors.body shouldBe
          listOf("Article by slug slug not found")
      }
    }

    "Can get an article by slug" {
      withServer { dependencies ->
        val userId =
          dependencies.userService
            .register(RegisterUser(validUsername, validEmail, validPw))
            .flatMap { JWT.decodeT(it.value, JWSHMAC512Algorithm) }
            .map { it.claimValueAsLong("id").shouldBeSome() }
            .shouldBeRight()

        val article =
          dependencies.articleService
            .createArticle(
              CreateArticle(UserId(userId), validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()

        val response = get("articles/${article.slug}")

        response.status shouldBe HttpStatusCode.OK
          response.body<SingleArticleResponse>().article shouldBe article
      }
    }
  })
