package io.github.nomisrev.articles

import arrow.core.raise.either
import io.github.nomisrev.NotArticleAuthor
import io.github.nomisrev.SuspendFun
import io.github.nomisrev.articleFixture
import io.github.nomisrev.registerUser
import io.github.nomisrev.withTestDependencies
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight

class ArticleServiceSpec :
    SuspendFun({
        "getUserFeed" -
            {
                "get empty user feed when the user follows nobody" {
                    withTestDependencies { dependencies ->
                        val user = dependencies.registerUser()
                        val otherUser = dependencies.registerUser()
                        val article = articleFixture()

                        val feed = either {
                            val _ =
                                dependencies.articleService.createArticle(
                                    CreateArticle(
                                        otherUser.userId,
                                        article.title,
                                        article.description,
                                        article.body,
                                        article.tags,
                                    )
                                )

                            dependencies.articleService.getUserFeed(
                                input = GetFeed(userId = user.userId, limit = 20, offset = 0)
                            )
                        }
                            .shouldBeRight()

                        assert(feed.articlesCount == 0)
                    }
                }

                "get user feed when the user follows another user" {
                    withTestDependencies { dependencies ->
                        val user = dependencies.registerUser()
                        val followed = dependencies.registerUser()
                        val unrelated = dependencies.registerUser()

                        either {
                            val _ =
                                dependencies.userPersistence.followProfile(
                                    followed.user.username,
                                    user.userId,
                                )

                            val followedArticle = articleFixture()
                            val createdFollowedArticle =
                                dependencies.articleService.createArticle(
                                    CreateArticle(
                                        followed.userId,
                                        followedArticle.title,
                                        followedArticle.description,
                                        followedArticle.body,
                                        followedArticle.tags,
                                    )
                                )

                            val unrelatedArticle = articleFixture()
                            val _ =
                                dependencies.articleService.createArticle(
                                    CreateArticle(
                                        unrelated.userId,
                                        unrelatedArticle.title,
                                        unrelatedArticle.description,
                                        unrelatedArticle.body,
                                        unrelatedArticle.tags,
                                    )
                                )

                            val feed =
                                dependencies.articleService.getUserFeed(
                                    input = GetFeed(userId = user.userId, limit = 20, offset = 0)
                                )
                            assert(feed.articlesCount == 1)
                            assert(feed.articles.single().slug == createdFollowedArticle.slug)
                        }
                            .shouldBeRight()
                    }
                }
            }

        "updateArticle" -
            {
                "allows the article author to update their own article" {
                    withTestDependencies { dependencies ->
                        val author = dependencies.registerUser()
                        val article = articleFixture()
                        either {
                            val created =
                                dependencies.articleService.createArticle(
                                    CreateArticle(
                                        author.userId,
                                        article.title,
                                        article.description,
                                        article.body,
                                        article.tags,
                                    )
                                )

                            val updated =
                                dependencies.articleService.updateArticle(
                                    UpdateArticleInput(
                                        slug = Slug(created.slug),
                                        userId = author.userId,
                                        title = "updated-title",
                                        description = "updated description",
                                        body = "updated body",
                                    )
                                )

                            assert(updated.slug == created.slug)
                            assert(updated.title == "updated-title")
                            assert(updated.description == "updated description")
                            assert(updated.body == "updated body")
                            assert(updated.author.username == author.user.username)
                        }
                            .shouldBeRight()
                    }
                }

                "rejects users who are not the article author" {
                    withTestDependencies { dependencies ->
                        val author = dependencies.registerUser()
                        val nonAuthor = dependencies.registerUser()

                        val article = articleFixture()
                        val created = either {
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    author.userId,
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
                                    userId = nonAuthor.userId,
                                    title = "updated-title",
                                    description = null,
                                    body = null,
                                )
                            )
                        } shouldBeLeft NotArticleAuthor(nonAuthor.userId.serial, created.slug)
                    }
                }
            }
    })
