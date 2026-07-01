package io.github.nomisrev.articles

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.DomainError
import io.github.nomisrev.NotArticleAuthor
import io.github.nomisrev.SuspendFun
import io.github.nomisrev.articleFixture
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.userFixture
import io.github.nomisrev.users.RegisterUser
import io.github.nomisrev.users.UserId
import io.github.nomisrev.withTestDependencies
import io.kotest.assertions.arrow.core.shouldBeLeft
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
                            either {
                                    dependencies.userService.register(
                                        RegisterUser(user.username, user.email, user.password)
                                    )
                                }
                                .shouldHaveUserId()

                        val otherUser = userFixture()
                        val otherUserId =
                            either {
                                    dependencies.userService.register(
                                        RegisterUser(
                                            otherUser.username,
                                            otherUser.email,
                                            otherUser.password,
                                        )
                                    )
                                }
                                .shouldHaveUserId()

                        val article = articleFixture()

                        val created =
                            either {
                                    dependencies.articleService.createArticle(
                                        CreateArticle(
                                            UserId(otherUserId),
                                            article.title,
                                            article.description,
                                            article.body,
                                            article.tags,
                                        )
                                    )
                                }
                                .shouldBeRight()

                        val feed =
                            either {
                                    dependencies.articleService.getUserFeed(
                                        input =
                                            GetFeed(userId = UserId(userId), limit = 20, offset = 0)
                                    )
                                }
                                .shouldBeRight()

                        assert(feed.articlesCount == 0)
                    }
                }

                "get user feed when the user follows another user" {
                    withTestDependencies { dependencies ->
                        val user = userFixture()
                        val userId =
                            either {
                                    dependencies.userService.register(
                                        RegisterUser(user.username, user.email, user.password)
                                    )
                                }
                                .shouldHaveUserId()
                        val followed = userFixture()
                        val followedId =
                            either {
                                    dependencies.userService.register(
                                        RegisterUser(
                                            followed.username,
                                            followed.email,
                                            followed.password,
                                        )
                                    )
                                }
                                .shouldHaveUserId()
                        val unrelated = userFixture()
                        val unrelatedId =
                            either {
                                    dependencies.userService.register(
                                        RegisterUser(
                                            unrelated.username,
                                            unrelated.email,
                                            unrelated.password,
                                        )
                                    )
                                }
                                .shouldHaveUserId()

                        either {
                                dependencies.userPersistence.followProfile(
                                    followed.username,
                                    UserId(userId),
                                )
                            }
                            .shouldBeRight()

                        val followedArticle = articleFixture()
                        val createdFollowedArticle =
                            either {
                                    dependencies.articleService.createArticle(
                                        CreateArticle(
                                            UserId(followedId),
                                            followedArticle.title,
                                            followedArticle.description,
                                            followedArticle.body,
                                            followedArticle.tags,
                                        )
                                    )
                                }
                                .shouldBeRight()

                        val unrelatedArticle = articleFixture()
                        either {
                                dependencies.articleService.createArticle(
                                    CreateArticle(
                                        UserId(unrelatedId),
                                        unrelatedArticle.title,
                                        unrelatedArticle.description,
                                        unrelatedArticle.body,
                                        unrelatedArticle.tags,
                                    )
                                )
                            }
                            .shouldBeRight()

                        val feed =
                            either {
                                    dependencies.articleService.getUserFeed(
                                        input =
                                            GetFeed(userId = UserId(userId), limit = 20, offset = 0)
                                    )
                                }
                                .shouldBeRight()

                        assert(feed.articlesCount == 1)
                        assert(feed.articles.single().slug == createdFollowedArticle.slug)
                    }
                }
            }

        "updateArticle" -
            {
                "allows the article author to update their own article" {
                    withTestDependencies { dependencies ->
                        val author = userFixture()
                        val authorId =
                            either {
                                    dependencies.userService.register(
                                        RegisterUser(author.username, author.email, author.password)
                                    )
                                }
                                .shouldHaveUserId()
                                .let(::UserId)

                        val article = articleFixture()
                        val created =
                            either {
                                    dependencies.articleService.createArticle(
                                        CreateArticle(
                                            authorId,
                                            article.title,
                                            article.description,
                                            article.body,
                                            article.tags,
                                        )
                                    )
                                }
                                .shouldBeRight()

                        val updated =
                            either {
                                    dependencies.articleService.updateArticle(
                                        UpdateArticleInput(
                                            slug = Slug(created.slug),
                                            userId = authorId,
                                            title = "updated-title",
                                            description = "updated description",
                                            body = "updated body",
                                        )
                                    )
                                }
                                .shouldBeRight()

                        assert(updated.slug == created.slug)
                        assert(updated.title == "updated-title")
                        assert(updated.description == "updated description")
                        assert(updated.body == "updated body")
                        assert(updated.author.username == author.username)
                    }
                }

                "rejects users who are not the article author" {
                    withTestDependencies { dependencies ->
                        val author = userFixture()
                        val authorId =
                            either {
                                    dependencies.userService.register(
                                        RegisterUser(author.username, author.email, author.password)
                                    )
                                }
                                .shouldHaveUserId()
                                .let(::UserId)

                        val nonAuthor = userFixture()
                        val nonAuthorId =
                            either {
                                    dependencies.userService.register(
                                        RegisterUser(
                                            nonAuthor.username,
                                            nonAuthor.email,
                                            nonAuthor.password,
                                        )
                                    )
                                }
                                .shouldHaveUserId()
                                .let(::UserId)

                        val article = articleFixture()
                        val created =
                            either {
                                    dependencies.articleService.createArticle(
                                        CreateArticle(
                                            authorId,
                                            article.title,
                                            article.description,
                                            article.body,
                                            article.tags,
                                        )
                                    )
                                }
                                .shouldBeRight()

                        either {
                            dependencies.articleService.updateArticle(
                                UpdateArticleInput(
                                    slug = Slug(created.slug),
                                    userId = nonAuthorId,
                                    title = "updated-title",
                                    description = null,
                                    body = null,
                                )
                            )
                        } shouldBeLeft NotArticleAuthor(nonAuthorId.serial, created.slug)
                    }
                }
            }
    })

fun Either<DomainError, JwtToken>.shouldHaveUserId() =
    flatMap { JWT.decodeT(it.value, JWSHMAC512Algorithm) }
        .map { it.claimValueAsLong("id").shouldBeSome() }
        .shouldBeRight()
