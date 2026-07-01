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
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
