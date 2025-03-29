package io.github.nomisrev.routes

import io.github.nomisrev.KotestProject
import io.github.nomisrev.MIN_FEED_LIMIT
import io.github.nomisrev.MIN_FEED_OFFSET
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.properties.Delegates

class ArticleRouteSpec :
  StringSpec({
    val username = "username3"
    val email = "valid1@domain.com"
    val password = "123456789"
    val tags = setOf("arrow", "ktor", "kotlin", "sqldelight")
    val title = "Fake Article Arrow"
    val description = "This is a fake article description."
    val body = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."

    var token: JwtToken by Delegates.notNull()

    beforeTest {
      token =
        KotestProject.dependencies
          .get()
          .userService
          .register(RegisterUser(username, email, password))
          .shouldBeRight()
    }

    "Check for empty feed" {
      withServer {
        val response =
          get(ArticleResource.Feed(offsetParam = 0)) {
            contentType(ContentType.Application.Json)
            bearerAuth(token.value)
          }

        assert(response.status == HttpStatusCode.OK)
        assert(
          response.body<ArticleWrapper<MultipleArticlesResponse>>().article.articles ==
            emptyList<Article>()
        )
        assert(response.body<ArticleWrapper<MultipleArticlesResponse>>().article.articlesCount == 0)
      }
    }

    "ٰValidate correct both offset and limit value" {
      withServer {
        val response =
          get(ArticleResource.Feed(offsetParam = 0, limitParam = 5)) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
          }

        assert(response.status == HttpStatusCode.OK)
        assert(
          response.body<ArticleWrapper<MultipleArticlesResponse>>().article.articles ==
            emptyList<Article>()
        )
        assert(response.body<ArticleWrapper<MultipleArticlesResponse>>().article.articlesCount == 0)
      }
    }

    "ٰValidate wrong offset value" {
      withServer {
        val response =
          get(ArticleResource.Feed(offsetParam = -1)) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
          }

        assert(response.status == HttpStatusCode.UnprocessableEntity)
        assert(
          response.body<GenericErrorModel>().errors.body ==
            listOf("feed offset: too small, minimum is $MIN_FEED_OFFSET, and found -1")
        )
      }
    }

    "ٰValidate wrong limit value" {
      withServer {
        val response =
          get(ArticleResource.Feed(offsetParam = 0, limitParam = 0)) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
          }

        assert(response.status == HttpStatusCode.UnprocessableEntity)
        assert(
          response.body<GenericErrorModel>().errors.body ==
            listOf("feed limit: too small, minimum is $MIN_FEED_LIMIT, and found 0")
        )
      }
    }

    "ٰValidate wrong both limit and value" {
      withServer {
        val response =
          get(ArticleResource.Feed(offsetParam = -1, limitParam = 0)) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
          }

        assert(response.status == HttpStatusCode.UnprocessableEntity)
        assert(
          response.body<GenericErrorModel>().errors.body ==
            listOf(
              "feed offset: too small, minimum is $MIN_FEED_OFFSET, and found -1",
              "feed limit: too small, minimum is $MIN_FEED_LIMIT, and found 0",
            )
        )
      }
    }

    "create article with tags" {
      withServer {
        val response =
          post(ArticlesResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(ArticleWrapper(NewArticle(title, description, body, tags.toList())))
          }

        assert(response.status == HttpStatusCode.Created)
        with(response.body<ArticleResponse>()) {
          assert(this.title == title)
          assert(this.description == description)
          assert(this.body == body)
          assert(this.favoritesCount == 0L)
          assert(this.favorited == false)
          assert(this.author.username == username)
          assert(this.tagList.toSet() == tags)
        }
      }
    }

    "article without tags" {
      withServer {
        val response =
          post(ArticlesResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(ArticleWrapper(NewArticle(title, description, body, emptyList())))
          }

        assert(response.status == HttpStatusCode.Created)
        with(response.body<ArticleResponse>()) {
          assert(this.title == title)
          assert(this.description == description)
          assert(this.body == body)
          assert(this.favoritesCount == 0L)
          assert(this.favorited == false)
          assert(this.author.username == username)
          assert(this.tagList.size == 0)
        }
      }
    }

    "body cannot be empty" {
      withServer {
        val response =
          post(ArticlesResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(ArticleWrapper(NewArticle(title, description, "", emptyList())))
          }

        assert(response.status == HttpStatusCode.UnprocessableEntity)
      }
    }

    "description cannot be empty" {
      withServer {
        val response =
          post(ArticlesResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(ArticleWrapper(NewArticle(title, "", body, emptyList())))
          }

        assert(response.status == HttpStatusCode.UnprocessableEntity)
      }
    }

    "title cannot be empty" {
      withServer {
        val response =
          post(ArticlesResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(ArticleWrapper(NewArticle("", description, body, emptyList())))
          }

        assert(response.status == HttpStatusCode.UnprocessableEntity)
      }
    }

    "Unauthorized user cannot create article" {
      withServer {
        val response =
          post(ArticlesResource()) {
            contentType(ContentType.Application.Json)
            setBody(ArticleWrapper(NewArticle(title, description, body, emptyList())))
          }

        assert(response.status == HttpStatusCode.Unauthorized)
      }
    }
  })
