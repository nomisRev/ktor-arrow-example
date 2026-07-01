package io.github.nomisrev.articles

import arrow.core.raise.either
import io.github.nomisrev.Api
import io.github.nomisrev.Api.Articles
import io.github.nomisrev.Api.Articles.Slug
import io.github.nomisrev.Api.Articles.Slug.Comments
import io.github.nomisrev.Api.Articles.Slug.Comments.create
import io.github.nomisrev.Api.Articles.Slug.Comments.list
import io.github.nomisrev.Api.Articles.Slug.get
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
import opensavvy.spine.api.invoke
import opensavvy.spine.client.bodyOrThrow
import opensavvy.spine.client.request

class ArticlesRouteSpec :
    StringSpec({
        "Article by slug not found" {
            withServer {
                val response = request(Api / Articles / Slug("slug") / get)

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.httpResponse.body<GenericErrorModel>().errors.body ==
                        listOf("Article by slug slug not found")
                )
            }
        }

        "Can get an article by slug" {
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

                val response = request(Api / Articles / Slug(created.slug) / get)

                val bodyOrThrow = response.bodyOrThrow()
                with(bodyOrThrow.article) {
                    assert(articleId == created.articleId)
                    assert(slug == created.slug)
                    assert(title == created.title)
                    assert(description == created.description)
                    assert(body == created.body)
                    assert(author == created.author)
                    assert(favorited == created.favorited)
                    assert(favoritesCount == created.favoritesCount)
                    assert(createdAt == created.createdAt)
                    assert(updatedAt == created.updatedAt)
                    assert(tagList.toSet() == created.tagList.toSet())
                }
            }
        }

        "can get comments for an article by slug when authenticated" {
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
                    request(Api / Articles / Slug(created.slug) / Comments / list) {
                        bearerAuth(token.value)
                    }

                val body = response.bodyOrThrow()
                assert(body.comments == emptyList<Comment>())
            }
        }

        "can not get comments for an article when not authenticated" {
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

                val response = request(Api / Articles / Slug(created.slug) / Comments / list)

                assert(response.httpResponse.status == HttpStatusCode.Unauthorized)
            }
        }

        "Can add a comment to an article" {
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

                val comment = "This is a comment ${user.username}"
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
                        Api / Articles / Slug(created.slug) / Comments / create,
                        CommentWrapper(NewComment(comment)),
                    ) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(token.value)
                    }

                val body = response.bodyOrThrow()
                assert(body.comment.body == comment)
                assert(body.comment.author.username == user.username)
            }
        }

        "Can not add a comment to an article with invalid token" {
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

                val comment = "This is a comment"
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
                        Api / Articles / Slug(created.slug) / Comments / create,
                        CommentWrapper(NewComment(comment)),
                    ) {
                        contentType(ContentType.Application.Json)
                        bearerAuth("invalid token")
                    }

                assert(response.httpResponse.status == HttpStatusCode.Unauthorized)
            }
        }

        "Can not add a comment to an article with empty body" {
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
                        Api / Articles / Slug(created.slug) / Comments / create,
                        CommentWrapper(NewComment("")),
                    ) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(token.value)
                    }

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
            }
        }
    })
