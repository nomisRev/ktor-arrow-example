package io.github.nomisrev.routes

import io.github.nomisrev.KotestProject
import io.github.nomisrev.MIN_FEED_SIZE
import io.github.nomisrev.MIN_OFFSET
import io.github.nomisrev.SuspendFun
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class ArticleRouteSpec :
  SuspendFun({
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

    "create" -
      {
        // User
        val validUsername = "username3"
        val validEmail = "valid1@domain.com"
        val validPw = "123456789"
        // Article
        val validTags = setOf("arrow", "ktor", "kotlin", "sqldelight")
        val validTitle = "Fake Article Arrow"
        val validDescription = "This is a fake article description."
        val validBody = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."

        var token: JwtToken? = null

        beforeTest {
          token =
            KotestProject.dependencies
              .get()
              .userService
              .register(RegisterUser(validUsername, validEmail, validPw))
              .shouldBeRight()
        }

        "article with tags" {
          withServer {
            val response =
              post(ArticlesResource()) {
                bearerAuth(token!!.value)
                contentType(ContentType.Application.Json)
                setBody(
                  ArticleWrapper(
                    NewArticle(validTitle, validDescription, validBody, validTags.toList())
                  )
                )
              }

            response.status shouldBe HttpStatusCode.Created
            with(response.body<ArticleResponse>()) {
              title shouldBe validTitle
              description shouldBe validDescription
              body shouldBe validBody
              favoritesCount shouldBe 0
              favorited shouldBe false
              author.username shouldBe validUsername
              tagList.toSet() shouldBe validTags
            }
          }
        }

        "article without tags" {
          withServer {
            val response =
              post(ArticlesResource()) {
                bearerAuth(token!!.value)
                contentType(ContentType.Application.Json)
                setBody(
                  ArticleWrapper(NewArticle(validTitle, validDescription, validBody, emptyList()))
                )
              }

            response.status shouldBe HttpStatusCode.Created
            with(response.body<ArticleResponse>()) {
              title shouldBe validTitle
              description shouldBe validDescription
              body shouldBe validBody
              favoritesCount shouldBe 0
              favorited shouldBe false
              author.username shouldBe validUsername
              tagList.size shouldBe 0
            }
          }
        }

        "body cannot be empty" {
          withServer {
            val response =
              post(ArticlesResource()) {
                bearerAuth(token!!.value)
                contentType(ContentType.Application.Json)
                setBody(ArticleWrapper(NewArticle(validTitle, validDescription, "", emptyList())))
              }

            response.status shouldBe HttpStatusCode.UnprocessableEntity
          }
        }

        "description cannot be empty" {
          withServer {
            val response =
              post(ArticlesResource()) {
                bearerAuth(token!!.value)
                contentType(ContentType.Application.Json)
                setBody(ArticleWrapper(NewArticle(validTitle, "", validBody, emptyList())))
              }

            response.status shouldBe HttpStatusCode.UnprocessableEntity
          }
        }

        "title cannot be empty" {
          withServer {
            val response =
              post(ArticlesResource()) {
                bearerAuth(token!!.value)
                contentType(ContentType.Application.Json)
                setBody(ArticleWrapper(NewArticle("", validDescription, validBody, emptyList())))
              }

            response.status shouldBe HttpStatusCode.UnprocessableEntity
          }
        }

        "Unauthorized user cannot create article" {
          withServer {
            val response =
              post(ArticlesResource()) {
                contentType(ContentType.Application.Json)
                setBody(
                  ArticleWrapper(NewArticle(validTitle, validDescription, validBody, emptyList()))
                )
              }

            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }
  })
