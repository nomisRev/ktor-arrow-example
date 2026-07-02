package io.github.nomisrev.articles

import arrow.core.raise.either
import io.github.nomisrev.NotArticleAuthor
import io.github.nomisrev.SuspendFun
import io.github.nomisrev.articleFixture
import io.github.nomisrev.registerUser
import io.github.nomisrev.userFixture
import io.github.nomisrev.withTestDependencies
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight

class ArticleServiceSpec :
    SuspendFun({
        "getUserFeed" -
            {
                "get empty user feed when the user follows nobody" {
                    withTestDependencies { dependencies ->
                        val (_, _, userId) = dependencies.registerUser()
                        val (_, _, otherUserId) = dependencies.registerUser()

                        val article = articleFixture()

                        val created =
                            either {
                                    dependencies.articleService.createArticle(
                                        CreateArticle(
                                            otherUserId,
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
                                        input = GetFeed(userId = userId, limit = 20, offset = 0)
                                    )
                                }
                                .shouldBeRight()

                        assert(feed.articlesCount == 0)
                    }
                }

                "get user feed when the user follows another user" {
                    withTestDependencies { dependencies ->
                        val (_, _, userId) = dependencies.registerUser()
                        val followed = userFixture()
                        val (_, _, followedId) = dependencies.registerUser(followed)
                        val (_, _, unrelatedId) = dependencies.registerUser()

                        either {
                                dependencies.userPersistence.followProfile(
                                    followed.username,
                                    userId,
                                )
                            }
                            .shouldBeRight()

                        val followedArticle = articleFixture()
                        val createdFollowedArticle =
                            either {
                                    dependencies.articleService.createArticle(
                                        CreateArticle(
                                            followedId,
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
                                        unrelatedId,
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
                                        input = GetFeed(userId = userId, limit = 20, offset = 0)
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
                        val (author, _, authorId) = dependencies.registerUser()

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
                        val (author, _, authorId) = dependencies.registerUser()
                        val (_, _, nonAuthorId) = dependencies.registerUser()

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
