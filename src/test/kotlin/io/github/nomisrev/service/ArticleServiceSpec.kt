package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.flatMap
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.*
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.repo.UserId
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.matchers.ints.shouldBeExactly

class ArticleServiceSpec :
  SuspendFun({
    val articleService: ArticleService = KotestProject.dependencies.get().articleService
    val userService: UserService = KotestProject.dependencies.get().userService

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

    "getUserFeed" -
      {
        "get empty kaaveh's feed when he followed nobody" {
          // Create users
          val kaavehId =
            userService
              .register(RegisterUser(kaavehUsername, kaavehEmail, kaavehPw))
              .shouldHaveUserId()
          val simonId =
            userService
              .register(RegisterUser(simonUsername, simonEmail, simonPw))
              .shouldHaveUserId()
          val johnId =
            userService
              .register(RegisterUser(johnUsername, johnEmail, johnPw))
              .shouldHaveUserId()

          // Create some articles
          articleService
            .createArticle(
              CreateArticle(UserId(simonId), validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()
          articleService
            .createArticle(
              CreateArticle(UserId(johnId), validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()

          // Get Kaaveh's feed
          val feed =
            articleService
              .getUserFeed(input = GetFeed(userId = UserId(kaavehId), limit = 20, offset = 0))
              .shouldBeRight()

          feed.articlesCount shouldBeExactly 0
        }
        "get kaaveh's feed when he followed simon" {
          // Create users
          val kaavehId =
            userService
              .register(RegisterUser(kaavehUsername, kaavehEmail, kaavehPw))
              .flatMap { JWT.decodeT(it.value, JWSHMAC512Algorithm) }
              .map { it.claimValueAsLong("id").shouldBeSome() }
              .shouldBeRight()
          val simonId =
            userService
              .register(RegisterUser(simonUsername, simonEmail, simonPw))
              .flatMap { JWT.decodeT(it.value, JWSHMAC512Algorithm) }
              .map { it.claimValueAsLong("id").shouldBeSome() }
              .shouldBeRight()
          val johnId =
            userService
              .register(RegisterUser(johnUsername, johnEmail, johnPw))
              .flatMap { JWT.decodeT(it.value, JWSHMAC512Algorithm) }
              .map { it.claimValueAsLong("id").shouldBeSome() }
              .shouldBeRight()

          // Create some articles
          articleService
            .createArticle(
              CreateArticle(UserId(simonId), validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()
          articleService
            .createArticle(
              CreateArticle(UserId(johnId), validTitle, validDescription, validBody, validTags)
            )
            .shouldBeRight()

          // Do Kaaveh follow Simon

          // TODO: From this point, first, issue #155 must be done.

        }
      }
  })

fun Either<DomainError, JwtToken>.shouldHaveUserId() =
  flatMap { JWT.decodeT(it.value, JWSHMAC512Algorithm) }
    .map { it.claimValueAsLong("id").shouldBeSome() }
    .shouldBeRight()