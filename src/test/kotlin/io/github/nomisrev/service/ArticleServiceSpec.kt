package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.flatMap
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.DomainError
import io.github.nomisrev.SuspendFun
import io.github.nomisrev.articleFixture
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.userFixture
import io.github.nomisrev.withTestDependencies
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome

class ArticleServiceSpec :
    SuspendFun({
        "getUserFeed" -
            {
                "get empty user feed when the user follows nobody" {
                    withTestDependencies { dependencies ->
                        val user = userFixture()
                        val userId =
                            dependencies.userService
                                .register(RegisterUser(user.username, user.email, user.password))
                                .shouldHaveUserId()

                        val otherUser = userFixture()
                        val otherUserId =
                            dependencies.userService
                                .register(
                                    RegisterUser(
                                        otherUser.username,
                                        otherUser.email,
                                        otherUser.password,
                                    )
                                )
                                .shouldHaveUserId()

                        val article = articleFixture()
                        dependencies.articleService
                            .createArticle(
                                CreateArticle(
                                    UserId(otherUserId),
                                    article.title,
                                    article.description,
                                    article.body,
                                    article.tags,
                                )
                            )
                            .shouldBeRight()

                        val feed =
                            dependencies.articleService
                                .getUserFeed(input = GetFeed(userId = UserId(userId), limit = 20, offset = 0))
              .shouldBeRight()

                        assert(feed.articlesCount == 0)
                    }
                }

                "get user feed when the user follows another user" {
                    withTestDependencies { dependencies ->
                        val user = userFixture()
                        val userId =
                            dependencies.userService
                                .register(RegisterUser(user.username, user.email, user.password))
                                .shouldHaveUserId()
                        val followed = userFixture()
                        val followedId =
                            dependencies.userService
                                .register(
                                    RegisterUser(
                                        followed.username,
                                        followed.email,
                                        followed.password,
                                    )
                                )
                                .shouldHaveUserId()
                        val unrelated = userFixture()
                        val unrelatedId =
                            dependencies.userService
                                .register(
                                    RegisterUser(
                                        unrelated.username,
                                        unrelated.email,
                                        unrelated.password,
                                    )
                                )
                                .shouldHaveUserId()

                        dependencies.userPersistence
                            .followProfile(followed.username, UserId(userId))
                            .shouldBeRight()

                        val followedArticle = articleFixture()
                        val createdFollowedArticle =
                            dependencies.articleService
                                .createArticle(
                                    CreateArticle(
                                        UserId(followedId),
                                        followedArticle.title,
                                        followedArticle.description,
                                        followedArticle.body,
                                        followedArticle.tags,
                                    )
                                )
                                .shouldBeRight()

                        val unrelatedArticle = articleFixture()
                        dependencies.articleService
                            .createArticle(
                                CreateArticle(
                                    UserId(unrelatedId),
                                    unrelatedArticle.title,
                                    unrelatedArticle.description,
                                    unrelatedArticle.body,
                                    unrelatedArticle.tags,
                                )
                            )
                            .shouldBeRight()

                        val feed =
                            dependencies.articleService.getUserFeed(
                                input = GetFeed(userId = UserId(userId), limit = 20, offset = 0)
                            )

                        assert(feed.articlesCount == 1)
                        assert(feed.articles.single().slug == createdFollowedArticle.slug)
                    }
                }
            }
    })

fun Either<DomainError, JwtToken>.shouldHaveUserId() =
    flatMap { JWT.decodeT(it.value, JWSHMAC512Algorithm) }
        .map { it.claimValueAsLong("id").shouldBeSome() }
        .shouldBeRight()
