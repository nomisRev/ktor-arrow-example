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
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.http.*

class TagRouteSpec :
  StringSpec({
    // User
    val validUsername = "username2"
    val validEmail = "valid2@domain.com"
    val validPw = "123456789"
    // Article
    val validTags = setOf("arrow", "ktor", "kotlin", "sqldelight")
    val validTitle = "Fake Article Arrow "
    val validDescription = "This is a fake article description."
    val validBody = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."

    "Check for empty list retrieval" {
      withServer {
        val response = get(TagsResource()) { contentType(ContentType.Application.Json) }

        assert(response.status == HttpStatusCode.OK)
        assert(response.body<TagsResponse>().tags.isEmpty())
      }
    }

    "Can get all tags" {
      withServer { dependencies ->
        val userId =
          dependencies.userService
            .register(RegisterUser(validUsername, validEmail, validPw))
            .flatMap { JWT.decodeT(it.value, JWSHMAC512Algorithm) }
            .map { it.claimValueAsLong("id").shouldBeSome() }
            .shouldBeRight()

        dependencies.articleService
          .createArticle(
            CreateArticle(UserId(userId), validTitle, validDescription, validBody, validTags)
          )
          .shouldBeRight()
        val response = get(TagsResource()) { contentType(ContentType.Application.Json) }

        assert(response.status == HttpStatusCode.OK)
        assert(response.body<TagsResponse>().tags.size == 4)
      }
    }
  })
