package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.*
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.FavouritePersistence
import io.github.nomisrev.repo.TagPersistence
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.matchers.ints.shouldBeExactly

class ArticleServiceSpec :
  SuspendFun({
    val kaavehUsername = "kaaveh"
    val simonUsername = "simon"
    val johnUsername = "john"

    val validTags = setOf("arrow", "ktor", "kotlin", "sqldelight")
    val validTitle = "Fake Article Arrow "
    val validDescription = "This is a fake article description."
    val validBody = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."

    "getUserFeed" -
      {
        "get empty kaaveh's feed when he followed nobody" {
          val kaavehId = registerUser(kaavehUsername).shouldHaveUserId()
          val simonId = registerUser(simonUsername).shouldHaveUserId()
          val johnId = registerUser(johnUsername).shouldHaveUserId()

          userService {
            createArticle(
              CreateArticle(UserId(simonId), validTitle, validDescription, validBody, validTags)
            )
            createArticle(
              CreateArticle(UserId(johnId), validTitle, validDescription, validBody, validTags)
            )

            getUserFeed(
              input = GetFeed(userId = UserId(kaavehId), limit = 20, offset = 0)
            ).articlesCount shouldBeExactly 0
          }

        }
        "get kaaveh's feed when he followed simon" {
          val simonId = registerUser(simonUsername).shouldHaveUserId()
          val johnId = registerUser(johnUsername).shouldHaveUserId()

          userService {
            createArticle(
              CreateArticle(UserId(simonId), validTitle, validDescription, validBody, validTags)
            )
            createArticle(
              CreateArticle(UserId(johnId), validTitle, validDescription, validBody, validTags)
            )
          }.shouldBeRight()
        }
      }
  })

private suspend fun <A> userService(
  block: suspend context(
  Raise<DomainError>,
  SlugGenerator,
  ArticlePersistence,
  TagPersistence,
  FavouritePersistence,
  UserPersistence
  ) () -> A
): Either<DomainError, A> = either {
  block(
    this,
    slugifyGenerator(),
    KotestProject.dependencies.get().articlePersistence,
    KotestProject.dependencies.get().tagPersistence,
    KotestProject.dependencies.get().favouritePersistence,
    KotestProject.dependencies.get().userPersistence,
  )
}

fun JwtToken.shouldHaveUserId(): Long =
  JWT.decodeT(value, JWSHMAC512Algorithm)
    .map { it.claimValueAsLong("id").shouldBeSome() }
    .shouldBeRight()
