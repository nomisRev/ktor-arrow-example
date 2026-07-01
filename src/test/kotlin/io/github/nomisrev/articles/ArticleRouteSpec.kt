package io.github.nomisrev.articles

import arrow.core.raise.either
import io.github.nomisrev.Api
import io.github.nomisrev.Api.Articles
import io.github.nomisrev.Api.Articles.create
import io.github.nomisrev.Api.Articles.feed
import io.github.nomisrev.Api.Articles.list
import io.github.nomisrev.GenericErrorModel
import io.github.nomisrev.articleFixture
import io.github.nomisrev.userFixture
import io.github.nomisrev.users.RegisterUser
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.div
import opensavvy.spine.client.bodyOrThrow
import opensavvy.spine.client.request

class ArticleRouteSpec :
    StringSpec({
        "Check for empty feed" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                        .shouldBeRight()

                val response =
                    request(
                        endpoint = Api / Articles / feed,
                        parameters = {
                            offset = 0
                        },
                    ) {
                        bearerAuth(token.value)
                    }

                val body = response.bodyOrThrow()
                assert(body.articles == emptyList<Article>())
                assert(body.articlesCount == 0)
            }
        }

        "ٰValidate correct both offset and limit value" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                        .shouldBeRight()

                val response =
                    request(
                        Api / Articles / feed,
                        parameters = {
                            offset = 0
                            limit = 5
                        },
                    ) {
                        bearerAuth(token.value)
                    }

                val body = response.bodyOrThrow()
                assert(body.articles == emptyList<Article>())
                assert(body.articlesCount == 0)
            }
        }

        "ٰValidate wrong offset value" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                        .shouldBeRight()

                val response =
                    request(
                        Api / Articles / feed,
                        parameters = {
                            offset = -1
                        },
                    ) {
                        bearerAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.httpResponse.body<GenericErrorModel>().errors.body ==
                        listOf("feed offset: too small, minimum is 0, and found -1")
                )
            }
        }

        "ٰValidate wrong limit value" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                        .shouldBeRight()

                val response =
                    request(
                        Api / Articles / feed,
                        parameters = {
                            offset = 0
                            limit = 0
                        },
                    ) {
                        bearerAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.httpResponse.body<GenericErrorModel>().errors.body ==
                        listOf("feed limit: too small, minimum is 1, and found 0")
                )
            }
        }

        "ٰValidate wrong both limit and value" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                        .shouldBeRight()

                val response =
                    request(
                        Api / Articles / feed,
                        parameters = {
                            offset = -1
                            limit = 0
                        },
                    ) {
                        bearerAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.httpResponse.body<GenericErrorModel>().errors.body ==
                        listOf(
                            "feed offset: too small, minimum is 0, and found -1",
                            "feed limit: too small, minimum is 1, and found 0",
                        )
                )
            }
        }

        "article list accepts OpenAPI offset and limit query parameters" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                        .shouldBeRight()
                val userId =
                    either { dependencies.jwtService.verifyJwtToken(token) }.shouldBeRight()
                val article = articleFixture()
                val created =
                    either {
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
                val author = userFixture()
                either {
                        val authorToken =
                            dependencies.userService.register(
                                RegisterUser(author.username, author.email, author.password)
                            )
                        val authorId = dependencies.jwtService.verifyJwtToken(authorToken)

                        val viewer = userFixture()
                        val viewerToken =
                            dependencies.userService.register(
                                RegisterUser(viewer.username, viewer.email, viewer.password)
                            )
                        val viewerId = dependencies.jwtService.verifyJwtToken(viewerToken)

                        val article = articleFixture()
                        val created =
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    authorId,
                                    article.title,
                                    article.description,
                                    article.body,
                                    article.tags,
                                )
                            )

                        dependencies.userPersistence.followProfile(author.username, viewerId)
                        dependencies.articleService.favoriteArticle(Slug(created.slug), viewerId)

                        val response =
                            request(
                                endpoint = Api / Articles / list,
                                parameters = {},
                            ) {
                                bearerAuth(viewerToken.value)
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
                val reader = userFixture()
                val followed = userFixture()
                val unrelated = userFixture()

                either {
                        val readerToken =
                            dependencies.userService.register(
                                RegisterUser(reader.username, reader.email, reader.password)
                            )
                        val readerId = dependencies.jwtService.verifyJwtToken(readerToken)

                        val followedToken =
                            dependencies.userService.register(
                                RegisterUser(followed.username, followed.email, followed.password)
                            )
                        val followedId = dependencies.jwtService.verifyJwtToken(followedToken)

                        val unrelatedToken =
                            dependencies.userService.register(
                                RegisterUser(
                                    unrelated.username,
                                    unrelated.email,
                                    unrelated.password,
                                )
                            )
                        val unrelatedId = dependencies.jwtService.verifyJwtToken(unrelatedToken)

                        dependencies.userPersistence.followProfile(followed.username, readerId)

                        val followedArticle = articleFixture()
                        val createdFollowedArticle =
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    followedId,
                                    followedArticle.title,
                                    followedArticle.description,
                                    followedArticle.body,
                                    followedArticle.tags,
                                )
                            )

                        val unrelatedArticle = articleFixture()
                        dependencies.articleService.createArticle(
                            CreateArticle(
                                unrelatedId,
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
                                bearerAuth(readerToken.value)
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
                val author = userFixture()
                val otherAuthor = userFixture()

                either {
                        val authorToken =
                            dependencies.userService.register(
                                RegisterUser(author.username, author.email, author.password)
                            )
                        val authorId = dependencies.jwtService.verifyJwtToken(authorToken)

                        val otherAuthorToken =
                            dependencies.userService.register(
                                RegisterUser(
                                    otherAuthor.username,
                                    otherAuthor.email,
                                    otherAuthor.password,
                                )
                            )
                        val otherAuthorId = dependencies.jwtService.verifyJwtToken(otherAuthorToken)

                        val article = articleFixture()
                        val created =
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    authorId,
                                    article.title,
                                    article.description,
                                    article.body,
                                    article.tags,
                                )
                            )

                        val otherArticle = articleFixture()
                        dependencies.articleService.createArticle(
                            CreateArticle(
                                otherAuthorId,
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
                                    this.author = author.username
                                },
                            )

                        val body: MultipleArticlesResponse = response.bodyOrThrow()
                        assert(body.articlesCount == 1)
                        assert(body.articles.single().slug == created.slug)
                        assert(body.articles.single().author.username == author.username)
                    }
                    .shouldBeRight()
            }
        }

        "article list filters by author when authenticated" {
            withServer { dependencies ->
                val author = userFixture()
                val viewer = userFixture()

                either {
                        val authorToken =
                            dependencies.userService.register(
                                RegisterUser(author.username, author.email, author.password)
                            )
                        val authorId = dependencies.jwtService.verifyJwtToken(authorToken)

                        val viewerToken =
                            dependencies.userService.register(
                                RegisterUser(viewer.username, viewer.email, viewer.password)
                            )
                        val viewerId = dependencies.jwtService.verifyJwtToken(viewerToken)

                        val article = articleFixture()
                        val created =
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    authorId,
                                    article.title,
                                    article.description,
                                    article.body,
                                    article.tags,
                                )
                            )

                        dependencies.userPersistence.followProfile(author.username, viewerId)

                        val response =
                            request(
                                Api / Articles / list,
                                parameters = {
                                    this.author = author.username
                                },
                            ) {
                                bearerAuth(viewerToken.value)
                            }

                        val body: MultipleArticlesResponse = response.bodyOrThrow()
                        val articleResponse = body.articles.single()
                        assert(body.articlesCount == 1)
                        assert(articleResponse.slug == created.slug)
                        assert(articleResponse.author.username == author.username)
                        assert(articleResponse.author.following)
                    }
                    .shouldBeRight()
            }
        }

        "article list filters by tag" {
            withServer { dependencies ->
                val author = userFixture()

                either {
                        val token =
                            dependencies.userService.register(
                                RegisterUser(author.username, author.email, author.password)
                            )
                        val authorId = dependencies.jwtService.verifyJwtToken(token)

                        val created =
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    authorId,
                                    "How to train your dragon",
                                    "Ever wonder how?",
                                    "Very carefully.",
                                    setOf("dragons", "training"),
                                )
                            )

                        dependencies.articleService.createArticle(
                            CreateArticle(
                                authorId,
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
                val author = userFixture()
                val viewer = userFixture()

                either {
                        val authorToken =
                            dependencies.userService.register(
                                RegisterUser(author.username, author.email, author.password)
                            )
                        val authorId = dependencies.jwtService.verifyJwtToken(authorToken)

                        val viewerToken =
                            dependencies.userService.register(
                                RegisterUser(viewer.username, viewer.email, viewer.password)
                            )
                        val viewerId = dependencies.jwtService.verifyJwtToken(viewerToken)

                        val article = articleFixture()
                        val created =
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    authorId,
                                    article.title,
                                    article.description,
                                    article.body,
                                    article.tags,
                                )
                            )

                        dependencies.articleService.favoriteArticle(Slug(created.slug), viewerId)

                        val response =
                            request(
                                Api / Articles / list,
                                parameters = {
                                    favorited = viewer.username
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
                val author = userFixture()
                val viewer = userFixture()

                either {
                        val authorToken =
                            dependencies.userService.register(
                                RegisterUser(author.username, author.email, author.password)
                            )
                        val authorId = dependencies.jwtService.verifyJwtToken(authorToken)

                        val viewerToken =
                            dependencies.userService.register(
                                RegisterUser(viewer.username, viewer.email, viewer.password)
                            )
                        val viewerId = dependencies.jwtService.verifyJwtToken(viewerToken)

                        val article = articleFixture()
                        val created =
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    authorId,
                                    article.title,
                                    article.description,
                                    article.body,
                                    article.tags,
                                )
                            )

                        dependencies.articleService.favoriteArticle(Slug(created.slug), viewerId)

                        val response =
                            request(
                                Api / Articles / list,
                                parameters = {
                                    favorited = viewer.username
                                },
                            ) {
                                bearerAuth(viewerToken.value)
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
                val user = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                        .shouldBeRight()
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
                        bearerAuth(token.value)
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
                val user = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                        .shouldBeRight()
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
                        bearerAuth(token.value)
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
                val user = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                        .shouldBeRight()
                val article = articleFixture()

                val response =
                    request(
                        Api / Articles / create,
                        ArticleWrapper(
                            NewArticle(article.title, article.description, "", emptyList())
                        ),
                    ) {
                        bearerAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
            }
        }

        "description cannot be empty" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                        .shouldBeRight()
                val article = articleFixture()

                val response =
                    request(
                        Api / Articles / create,
                        ArticleWrapper(NewArticle(article.title, "", article.body, emptyList())),
                    ) {
                        bearerAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
            }
        }

        "title cannot be empty" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    either {
                            dependencies.userService.register(
                                RegisterUser(user.username, user.email, user.password)
                            )
                        }
                        .shouldBeRight()
                val article = articleFixture()

                val response =
                    request(
                        Api / Articles / create,
                        ArticleWrapper(
                            NewArticle("", article.description, article.body, emptyList())
                        ),
                    ) {
                        bearerAuth(token.value)
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
