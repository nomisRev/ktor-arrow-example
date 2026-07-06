package io.github.nomisrev.articles

import arrow.core.raise.either
import io.github.nomisrev.Api
import io.github.nomisrev.Api.Articles
import io.github.nomisrev.Api.Articles.create
import io.github.nomisrev.Api.Articles.feed
import io.github.nomisrev.Api.Articles.list
import io.github.nomisrev.GenericErrorModel
import io.github.nomisrev.articleFixture
import io.github.nomisrev.registerUser
import io.github.nomisrev.tokenAuth
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.div
import opensavvy.spine.client.bodyOrThrow
import opensavvy.spine.client.request

class ArticleRouteSpec :
    StringSpec({
        "Check for empty feed" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()

                val response =
                    request(
                        endpoint = Api / Articles / feed,
                        parameters = {
                            offset = 0
                        },
                    ) {
                        tokenAuth(token.value)
                    }

                val body = response.bodyOrThrow()
                assert(body.articles == emptyList<Article>())
                assert(body.articlesCount == 0)
            }
        }

        "ٰValidate correct both offset and limit value" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()

                val response =
                    request(
                        Api / Articles / feed,
                        parameters = {
                            offset = 0
                            limit = 5
                        },
                    ) {
                        tokenAuth(token.value)
                    }

                val body = response.bodyOrThrow()
                assert(body.articles == emptyList<Article>())
                assert(body.articlesCount == 0)
            }
        }

        "ٰValidate wrong offset value" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()

                val response =
                    request(
                        Api / Articles / feed,
                        parameters = {
                            offset = -1
                        },
                    ) {
                        tokenAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.httpResponse.body<GenericErrorModel>().errors.body ==
                        ["feed offset: too small, minimum is 0, and found -1"]
                )
            }
        }

        "ٰValidate wrong limit value" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()

                val response =
                    request(
                        Api / Articles / feed,
                        parameters = {
                            offset = 0
                            limit = 0
                        },
                    ) {
                        tokenAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.httpResponse.body<GenericErrorModel>().errors.body ==
                        ["feed limit: too small, minimum is 1, and found 0"]
                )
            }
        }

        "ٰValidate wrong both limit and value" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()

                val response =
                    request(
                        Api / Articles / feed,
                        parameters = {
                            offset = -1
                            limit = 0
                        },
                    ) {
                        tokenAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.httpResponse.body<GenericErrorModel>().errors.body ==
                        [
                            "feed offset: too small, minimum is 0, and found -1",
                            "feed limit: too small, minimum is 1, and found 0",
                        ]
                )
            }
        }

        "article list accepts OpenAPI offset and limit query parameters" {
            withServer { dependencies ->
                val (userId) = dependencies.registerUser()
                val article = articleFixture()
                val created = either {
                    dependencies.articleService.createArticle(
                        CreateArticle(
                            userId,
                            article.title,
                            article.description,
                            article.body,
                            article.tags,
                        )
                    )
                }
                    .shouldBeRight()

                val response =
                    request(
                        Api / Articles / list,
                        parameters = {
                            offset = 0
                            limit = 1
                        },
                    )

                val body = response.bodyOrThrow()
                assert(body.articlesCount == 1)
                assert(body.articles.single().slug == created.slug)
            }
        }

        "article list returns viewer specific metadata" {
            withServer { dependencies ->
                val author = dependencies.registerUser()
                val viewer = dependencies.registerUser()
                either {
                    val article = articleFixture()
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

                    dependencies.userPersistence.followProfile(
                        author.user.username,
                        viewer.userId,
                    )
                    dependencies.articleService.favoriteArticle(
                        Slug(created.slug),
                        viewer.userId,
                    )

                    val response =
                        request(
                            endpoint = Api / Articles / list,
                            parameters = {},
                        ) {
                            tokenAuth(viewer.token.value)
                        }

                    val body: MultipleArticlesResponse = response.bodyOrThrow()
                    val articleResponse = body.articles.single()
                    assert(articleResponse.slug == created.slug)
                    assert(articleResponse.favorited)
                    assert(articleResponse.favoritesCount == 1L)
                    assert(articleResponse.author.following)
                }
                    .shouldBeRight()
            }
        }

        "feed returns articles from followed authors" {
            withServer { dependencies ->
                val reader = dependencies.registerUser()
                val followed = dependencies.registerUser()
                val unrelated = dependencies.registerUser()

                either {
                    dependencies.userPersistence.followProfile(
                        followed.user.username,
                        reader.userId,
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
                    dependencies.articleService.createArticle(
                        CreateArticle(
                            unrelated.userId,
                            unrelatedArticle.title,
                            unrelatedArticle.description,
                            unrelatedArticle.body,
                            unrelatedArticle.tags,
                        )
                    )

                    val response =
                        request(
                            endpoint = Api / Articles / feed,
                            parameters = {
                                offset = 0
                                limit = 20
                            },
                        ) {
                            tokenAuth(reader.token.value)
                        }

                    val body: MultipleArticlesResponse = response.bodyOrThrow()
                    assert(body.articlesCount == 1)
                    assert(body.articles.single().slug == createdFollowedArticle.slug)
                }
                    .shouldBeRight()
            }
        }

        "article list filters by author" {
            withServer { dependencies ->
                val author = dependencies.registerUser()
                val otherAuthor = dependencies.registerUser()

                either {
                    val article = articleFixture()
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

                    val otherArticle = articleFixture()
                    dependencies.articleService.createArticle(
                        CreateArticle(
                            otherAuthor.userId,
                            otherArticle.title,
                            otherArticle.description,
                            otherArticle.body,
                            otherArticle.tags,
                        )
                    )

                    val response =
                        request(
                            Api / Articles / list,
                            parameters = {
                                this.author = author.user.username
                            },
                        )

                    val body: MultipleArticlesResponse = response.bodyOrThrow()
                    assert(body.articlesCount == 1)
                    assert(body.articles.single().slug == created.slug)
                    assert(body.articles.single().author.username == author.user.username)
                }
                    .shouldBeRight()
            }
        }

        "article list filters by author when authenticated" {
            withServer { dependencies ->
                val author = dependencies.registerUser()
                val viewer = dependencies.registerUser()

                either {
                    val article = articleFixture()
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

                    dependencies.userPersistence.followProfile(
                        author.user.username,
                        viewer.userId,
                    )

                    val response =
                        request(
                            Api / Articles / list,
                            parameters = {
                                this.author = author.user.username
                            },
                        ) {
                            tokenAuth(viewer.token.value)
                        }

                    val body: MultipleArticlesResponse = response.bodyOrThrow()
                    val articleResponse = body.articles.single()
                    assert(body.articlesCount == 1)
                    assert(articleResponse.slug == created.slug)
                    assert(articleResponse.author.username == author.user.username)
                    assert(articleResponse.author.following)
                }
                    .shouldBeRight()
            }
        }

        "article list filters by tag" {
            withServer { dependencies ->
                val (userId) = dependencies.registerUser()

                either {
                    val created =
                        dependencies.articleService.createArticle(
                            CreateArticle(
                                userId,
                                "How to train your dragon",
                                "Ever wonder how?",
                                "Very carefully.",
                                setOf("dragons", "training"),
                            )
                        )

                    dependencies.articleService.createArticle(
                        CreateArticle(
                            userId,
                            "Something else",
                            "Nothing about dragons",
                            "Still interesting.",
                            setOf("kotlin"),
                        )
                    )

                    val response =
                        request(
                            Api / Articles / list,
                            parameters = {
                                tag = "dragons"
                            },
                        )

                    val body: MultipleArticlesResponse = response.bodyOrThrow()
                    assert(body.articlesCount == 1)
                    val articleResponse = body.articles.single()
                    assert(articleResponse.slug == created.slug)
                    assert(articleResponse.tagList.contains("dragons"))
                    assert(articleResponse.tagList.contains("training"))
                }
                    .shouldBeRight()
            }
        }

        "article list filters by favorited username" {
            withServer { dependencies ->
                val author = dependencies.registerUser()
                val viewer = dependencies.registerUser()

                either {
                    val article = articleFixture()
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

                    dependencies.articleService.favoriteArticle(
                        Slug(created.slug),
                        viewer.userId,
                    )

                    val response =
                        request(
                            Api / Articles / list,
                            parameters = {
                                favorited = viewer.user.username
                            },
                        )

                    val body: MultipleArticlesResponse = response.bodyOrThrow()
                    assert(body.articlesCount == 1)
                    val articleResponse = body.articles.single()
                    assert(articleResponse.slug == created.slug)
                    assert(articleResponse.favoritesCount == 1L)
                }
                    .shouldBeRight()
            }
        }

        "article list filters by favorited username when authenticated" {
            withServer { dependencies ->
                val author = dependencies.registerUser()
                val viewer = dependencies.registerUser()

                either {
                    val article = articleFixture()
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

                    dependencies.articleService.favoriteArticle(
                        Slug(created.slug),
                        viewer.userId,
                    )

                    val response =
                        request(
                            Api / Articles / list,
                            parameters = {
                                favorited = viewer.user.username
                            },
                        ) {
                            tokenAuth(viewer.token.value)
                        }

                    val body: MultipleArticlesResponse = response.bodyOrThrow()
                    assert(body.articlesCount == 1)
                    val articleResponse = body.articles.single()
                    assert(articleResponse.slug == created.slug)
                    assert(articleResponse.favorited)
                    assert(articleResponse.favoritesCount == 1L)
                }
                    .shouldBeRight()
            }
        }

        "create article with tags" {
            withServer { dependencies ->
                val (user, token) = dependencies.registerUser()
                val article = articleFixture()

                val response =
                    request(
                        Api / Articles / create,
                        ArticleWrapper(
                            NewArticle(
                                article.title,
                                article.description,
                                article.body,
                                article.tags.toList(),
                            )
                        ),
                    ) {
                        tokenAuth(token.value)
                    }

                val created = response.bodyOrThrow()
                assert(created.article.title == article.title)
                assert(created.article.description == article.description)
                assert(created.article.body == article.body)
                assert(created.article.favoritesCount == 0L)
                assert(!created.article.favorited)
                assert(created.article.author.username == user.username)
                assert(created.article.tagList.toSet() == article.tags)
                assert(response.httpResponse.status == HttpStatusCode.Created)
            }
        }

        "article without tags" {
            withServer { dependencies ->
                val (user, token) = dependencies.registerUser()
                val article = articleFixture()

                val response =
                    request(
                        Api / Articles / create,
                        ArticleWrapper(
                            NewArticle(
                                article.title,
                                article.description,
                                article.body,
                                emptyList(),
                            )
                        ),
                    ) {
                        tokenAuth(token.value)
                    }

                val created = response.bodyOrThrow()
                assert(created.article.title == article.title)
                assert(created.article.description == article.description)
                assert(created.article.body == article.body)
                assert(created.article.favoritesCount == 0L)
                assert(!created.article.favorited)
                assert(created.article.author.username == user.username)
                assert(created.article.tagList.isEmpty())
                assert(response.httpResponse.status == HttpStatusCode.Created)
            }
        }

        "body cannot be empty" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()
                val article = articleFixture()

                val response =
                    request(
                        Api / Articles / create,
                        ArticleWrapper(
                            NewArticle(article.title, article.description, "", emptyList())
                        ),
                    ) {
                        tokenAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
            }
        }

        "description cannot be empty" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()
                val article = articleFixture()

                val response =
                    request(
                        Api / Articles / create,
                        ArticleWrapper(NewArticle(article.title, "", article.body, emptyList())),
                    ) {
                        tokenAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
            }
        }

        "title cannot be empty" {
            withServer { dependencies ->
                val (token) = dependencies.registerUser()
                val article = articleFixture()

                val response =
                    request(
                        Api / Articles / create,
                        ArticleWrapper(
                            NewArticle("", article.description, article.body, emptyList())
                        ),
                    ) {
                        tokenAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
            }
        }

        "Unauthorized user cannot create article" {
            withServer {
                val article = articleFixture()
                val response =
                    request(
                        Api / Articles / create,
                        ArticleWrapper(
                            NewArticle(
                                article.title,
                                article.description,
                                article.body,
                                emptyList(),
                            )
                        ),
                    )

                assert(response.httpResponse.status == HttpStatusCode.Unauthorized)
            }
        }
    })
