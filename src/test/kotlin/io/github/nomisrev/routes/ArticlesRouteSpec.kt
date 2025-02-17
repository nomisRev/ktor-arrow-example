package io.github.nomisrev.routes

import io.github.nomisrev.KotestProject
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.service.CreateArticle
import io.github.nomisrev.service.Login
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

    var token: JwtToken by Delegates.notNull()
    var userId: UserId by Delegates.notNull()

    beforeAny {
      KotestProject.dependencies
        .get()
        .userService
        .register(RegisterUser(validUsername, validEmail, validPw))
        .shouldBeRight()
    }

    beforeTest {
      token =
        KotestProject.dependencies
          .get()
          .userService
          .login(Login(validEmail, validPw))
          .shouldBeRight()
          .first
      userId = KotestProject.dependencies.get().jwtService.verifyJwtToken(token).shouldBeRight()
    }

    "Article by slug not found" {
      withServer {
        val response = get(ArticlesResource.Slug(slug = "slug"))

        assert(response.status == HttpStatusCode.UnprocessableEntity)
        assert(
          response.body<GenericErrorModel>().errors.body == listOf("Article by slug slug not found")
        )
      }
    }

    "Can get an article by slug" {
      withServer { dependencies ->
        val article =
          dependencies.articleService
            .createArticle(
              CreateArticle(userId, validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()

        val response = get(ArticlesResource.Slug(slug = article.slug))

        assert(response.status == HttpStatusCode.OK)
        assert(response.body<SingleArticleResponse>().article == article)
      }
    }

    "can get comments for an article by slug when authenticated" {
      withServer { dependencies ->
        val article =
          dependencies.articleService
            .createArticle(
              CreateArticle(userId, validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()

        val response =
          get(ArticlesResource.Comments(slug = article.slug)) { bearerAuth(token.value) }

        assert(response.status == HttpStatusCode.OK)
        assert(response.body<MultipleCommentsResponse>().comments == emptyList<Comment>())
      }
    }

    "can not get comments for an article when not authenticated" {
      withServer { dependencies ->
        val article =
          dependencies.articleService
            .createArticle(
              CreateArticle(userId, validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()

        val response = get(ArticlesResource.Comments(slug = article.slug))

        assert(response.status == HttpStatusCode.Unauthorized)
      }
    }

    "Can add a comment to an article" {
      withServer { dependencies ->
        val comment = "This is a comment"
        val article =
          dependencies.articleService
            .createArticle(
              CreateArticle(userId, validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()

        val response =
          post(ArticlesResource.Comments(slug = article.slug)) {
            contentType(ContentType.Application.Json)
            bearerAuth(token.value)
            setBody(NewComment(comment))
          }

        assert(response.status == HttpStatusCode.OK)
        with(response.body<SingleCommentResponse>()) {
          assert(this.comment.body == comment)
          assert(this.comment.author.username == validUsername)
        }
      }
    }

    "Can not add a comment to an article with invalid token" {
      withServer { dependencies ->
        val comment = "This is a comment"
        val article =
          dependencies.articleService
            .createArticle(
              CreateArticle(userId, validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()

        val response =
          post(ArticlesResource.Comments(slug = article.slug)) {
            contentType(ContentType.Application.Json)
            bearerAuth("invalid token")
            setBody(NewComment(comment))
          }

        assert(response.status == HttpStatusCode.Unauthorized)
      }
    }

    "Can not add a comment to an article with empty body" {
      withServer { dependencies ->
        val comment = ""
        val article =
          dependencies.articleService
            .createArticle(
              CreateArticle(userId, validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()

        val response =
          post(ArticlesResource.Comments(slug = article.slug)) {
            contentType(ContentType.Application.Json)
            bearerAuth(token.value)
            setBody(NewComment(comment))
          }

        assert(response.status == HttpStatusCode.UnprocessableEntity)
      }
    }
  })
