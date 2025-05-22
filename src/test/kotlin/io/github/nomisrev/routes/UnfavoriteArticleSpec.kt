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
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.bearerAuth
import io.ktor.http.HttpStatusCode
import kotlin.properties.Delegates

class UnfavoriteArticleSpec :
  StringSpec({
    // User
    val validUsername = "username_unfavorite"
    val validEmail = "unfavorite@domain.com"
    val validPw = "123456789"

    // Article
    val validTags = setOf("arrow", "kotlin", "ktor", "sqldelight")
    val validTitle = "Unfavorite Article Test"
    val validDescription = "This is a test article for unfavoriting."
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

    "Can unfavorite an article" {
      withServer { dependencies ->
        // Create an article
        val article =
          dependencies.articleService
            .createArticle(
              CreateArticle(userId, validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()

        // Favorite the article
        val favoriteResponse =
          post(ArticlesResource.Slug.Favorite(ArticlesResource.Slug(slug = article.slug))) {
            bearerAuth(token.value)
          }

        assert(favoriteResponse.status == HttpStatusCode.OK)
        val favoritedArticle = favoriteResponse.body<SingleArticleResponse>().article
        assert(favoritedArticle.favorited)
        assert(favoritedArticle.favoritesCount == 1L)

        // Unfavorite the article
        val unfavoriteResponse =
          delete(ArticlesResource.Slug.Favorite(ArticlesResource.Slug(slug = article.slug))) {
            bearerAuth(token.value)
          }

        assert(unfavoriteResponse.status == HttpStatusCode.OK)
        val unfavoritedArticle = unfavoriteResponse.body<SingleArticleResponse>().article
        assert(!unfavoritedArticle.favorited)
        assert(unfavoritedArticle.favoritesCount == 0L)
      }
    }

    "Cannot unfavorite an article without authentication" {
      withServer { dependencies ->
        // Create an article
        val article =
          dependencies.articleService
            .createArticle(
              CreateArticle(userId, validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()

        // Try to unfavorite without authentication
        val response =
          delete(ArticlesResource.Slug.Favorite(ArticlesResource.Slug(slug = article.slug)))

        assert(response.status == HttpStatusCode.Unauthorized)
      }
    }

    "Cannot unfavorite a non-existent article" {
      withServer {
        // Try to unfavorite a non-existent article
        val response =
          delete(ArticlesResource.Slug.Favorite(ArticlesResource.Slug(slug = "non-existent-slug"))) {
            bearerAuth(token.value)
          }

        assert(response.status == HttpStatusCode.UnprocessableEntity)
        assert(
          response.body<GenericErrorModel>().errors.body == 
            listOf("Article by slug non-existent-slug not found")
        )
      }
    }
  })