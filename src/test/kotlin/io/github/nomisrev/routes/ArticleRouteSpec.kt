package io.github.nomisrev.routes

import io.github.nomisrev.articleFixture
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.userFixture
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class ArticleRouteSpec :
    StringSpec({
        "Check for empty feed" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()

                val response =
                    get(ArticleResource.Feed(offsetParam = 0)) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(token.value)
                    }

                assert(response.status == HttpStatusCode.OK)
                val body = response.body<MultipleArticlesResponse>()
                assert(body.articles == emptyList<Article>())
                assert(body.articlesCount == 0)
            }
        }

        "ٰValidate correct both offset and limit value" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()

                val response =
                    get(ArticleResource.Feed(offsetParam = 0, limitParam = 5)) {
                        bearerAuth(token.value)
                        contentType(ContentType.Application.Json)
                    }

                assert(response.status == HttpStatusCode.OK)
                val body = response.body<MultipleArticlesResponse>()
                assert(body.articles == emptyList<Article>())
                assert(body.articlesCount == 0)
            }
        }

        "ٰValidate wrong offset value" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()

                val response =
                    get(ArticleResource.Feed(offsetParam = -1)) {
                        bearerAuth(token.value)
                        contentType(ContentType.Application.Json)
                    }

                assert(response.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.body<GenericErrorModel>().errors.body ==
                        listOf("feed offset: too small, minimum is 0, and found -1")
                )
            }
        }

        "ٰValidate wrong limit value" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()

                val response =
                    get(ArticleResource.Feed(offsetParam = 0, limitParam = 0)) {
                        bearerAuth(token.value)
                        contentType(ContentType.Application.Json)
                    }

                assert(response.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.body<GenericErrorModel>().errors.body ==
                        listOf("feed limit: too small, minimum is 1, and found 0")
                )
            }
        }

        "ٰValidate wrong both limit and value" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()

                val response =
                    get(ArticleResource.Feed(offsetParam = -1, limitParam = 0)) {
                        bearerAuth(token.value)
                        contentType(ContentType.Application.Json)
                    }

                assert(response.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.body<GenericErrorModel>().errors.body ==
                        listOf(
                            "feed offset: too small, minimum is 0, and found -1",
                            "feed limit: too small, minimum is 1, and found 0",
                        )
                )
            }
        }

        "create article with tags" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val article = articleFixture()

                val response =
                    post(ArticlesResource()) {
                        bearerAuth(token.value)
                        contentType(ContentType.Application.Json)
                        setBody(
                            ArticleWrapper(
                                NewArticle(
                                    article.title,
                                    article.description,
                                    article.body,
                                    article.tags.toList(),
                                )
                            )
                        )
                    }

                assert(response.status == HttpStatusCode.Created)
                with(response.body<SingleArticleResponse>().article) {
                    assert(this.title == article.title)
                    assert(this.description == article.description)
                    assert(this.body == article.body)
                    assert(this.favoritesCount == 0L)
                    assert(this.favorited == false)
                    assert(this.author.username == user.username)
                    assert(this.tagList.toSet() == article.tags)
                }
            }
        }

        "article without tags" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val article = articleFixture()

                val response =
                    post(ArticlesResource()) {
                        bearerAuth(token.value)
                        contentType(ContentType.Application.Json)
                        setBody(
                            ArticleWrapper(
                                NewArticle(
                                    article.title,
                                    article.description,
                                    article.body,
                                    emptyList(),
                                )
                            )
                        )
                    }

                assert(response.status == HttpStatusCode.Created)
                with(response.body<SingleArticleResponse>().article) {
                    assert(this.title == article.title)
                    assert(this.description == article.description)
                    assert(this.body == article.body)
                    assert(this.favoritesCount == 0L)
                    assert(this.favorited == false)
                    assert(this.author.username == user.username)
                    assert(this.tagList.size == 0)
                }
            }
        }

        "body cannot be empty" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val article = articleFixture()

                val response =
                    post(ArticlesResource()) {
                        bearerAuth(token.value)
                        contentType(ContentType.Application.Json)
                        setBody(
                            ArticleWrapper(
                                NewArticle(article.title, article.description, "", emptyList())
                            )
                        )
                    }

                assert(response.status == HttpStatusCode.UnprocessableEntity)
            }
        }

        "description cannot be empty" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val article = articleFixture()

                val response =
                    post(ArticlesResource()) {
                        bearerAuth(token.value)
                        contentType(ContentType.Application.Json)
                        setBody(
                            ArticleWrapper(NewArticle(article.title, "", article.body, emptyList()))
                        )
                    }

                assert(response.status == HttpStatusCode.UnprocessableEntity)
            }
        }

        "title cannot be empty" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val article = articleFixture()

                val response =
                    post(ArticlesResource()) {
                        bearerAuth(token.value)
                        contentType(ContentType.Application.Json)
                        setBody(
                            ArticleWrapper(
                                NewArticle("", article.description, article.body, emptyList())
                            )
                        )
                    }

                assert(response.status == HttpStatusCode.UnprocessableEntity)
            }
        }

        "Unauthorized user cannot create article" {
            withServer {
                val article = articleFixture()
                val response =
                    post(ArticlesResource()) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            ArticleWrapper(
                                NewArticle(
                                    article.title,
                                    article.description,
                                    article.body,
                                    emptyList(),
                                )
                            )
                        )
                    }

                assert(response.status == HttpStatusCode.Unauthorized)
            }
        }
    })
