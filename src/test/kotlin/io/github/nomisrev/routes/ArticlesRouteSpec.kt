package io.github.nomisrev.routes

import io.github.nomisrev.articleFixture
import io.github.nomisrev.service.CreateArticle
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

class ArticlesRouteSpec :
    StringSpec({


        "Article by slug not found" {
            withServer {
                val response = get(ArticlesResource.Slug(slug = "slug"))

                assert(response.status == HttpStatusCode.UnprocessableEntity)
                assert(
                    response.body<GenericErrorModel>().errors.body ==
                        listOf("Article by slug slug not found")
                )
            }
        }

        "Can get an article by slug" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val userId = dependencies.jwtService.verifyJwtToken(token).shouldBeRight()
                val article = articleFixture()
                val created =
                    dependencies.articleService
                        .createArticle(
                            CreateArticle(
                                userId,
                                article.title,
                                article.description,
                                article.body,
                                article.tags,
                            )
                        )
                        .shouldBeRight()

                val response = get(ArticlesResource.Slug(slug = created.slug))

                assert(response.status == HttpStatusCode.OK)
                with(response.body<SingleArticleResponse>().article) {
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
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val userId = dependencies.jwtService.verifyJwtToken(token).shouldBeRight()
                val article = articleFixture()
                val created =
                    dependencies.articleService
                        .createArticle(
                            CreateArticle(
                                userId,
                                article.title,
                                article.description,
                                article.body,
                                article.tags,
                            )
                        )
                        .shouldBeRight()

                val response =
                    get(ArticlesResource.Comments(slug = created.slug)) { bearerAuth(token.value) }

                assert(response.status == HttpStatusCode.OK)
                assert(response.body<MultipleCommentsResponse>().comments == emptyList<Comment>())
            }
        }

        "can not get comments for an article when not authenticated" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val userId = dependencies.jwtService.verifyJwtToken(token).shouldBeRight()
                val article = articleFixture()
                val created =
                    dependencies.articleService
                        .createArticle(
                            CreateArticle(
                                userId,
                                article.title,
                                article.description,
                                article.body,
                                article.tags,
                            )
                        )
                        .shouldBeRight()

                val response = get(ArticlesResource.Comments(slug = created.slug))

                assert(response.status == HttpStatusCode.Unauthorized)
            }
        }

        "Can add a comment to an article" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val userId = dependencies.jwtService.verifyJwtToken(token).shouldBeRight()
                val comment = "This is a comment ${user.username}"
                val article = articleFixture()
                val created =
                    dependencies.articleService
                        .createArticle(
                            CreateArticle(
                                userId,
                                article.title,
                                article.description,
                                article.body,
                                article.tags,
                            )
                        )
                        .shouldBeRight()

                val response =
                    post(ArticlesResource.Comments(slug = created.slug)) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(token.value)
                        setBody(CommentWrapper(NewComment(comment)))
                    }

                assert(response.status == HttpStatusCode.OK)
                with(response.body<SingleCommentResponse>()) {
                    assert(this.comment.body == comment)
                    assert(this.comment.author.username == user.username)
                }
            }
        }

        "Can not add a comment to an article with invalid token" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val userId = dependencies.jwtService.verifyJwtToken(token).shouldBeRight()
                val comment = "This is a comment"
                val article = articleFixture()
                val created =
                    dependencies.articleService
                        .createArticle(
                            CreateArticle(
                                userId,
                                article.title,
                                article.description,
                                article.body,
                                article.tags,
                            )
                        )
                        .shouldBeRight()

                val response =
                    post(ArticlesResource.Comments(slug = created.slug)) {
                        contentType(ContentType.Application.Json)
                        bearerAuth("invalid token")
                        setBody(CommentWrapper(NewComment(comment)))
                    }

                assert(response.status == HttpStatusCode.Unauthorized)
            }
        }

        "Can not add a comment to an article with empty body" {
            withServer { dependencies ->
                val user = userFixture()
                val token =
                    dependencies.userService
                        .register(RegisterUser(user.username, user.email, user.password))
                        .shouldBeRight()
                val userId = dependencies.jwtService.verifyJwtToken(token).shouldBeRight()
                val article = articleFixture()
                val created =
                    dependencies.articleService
                        .createArticle(
                            CreateArticle(
                                userId,
                                article.title,
                                article.description,
                                article.body,
                                article.tags,
                            )
                        )
                        .shouldBeRight()

                val response =
                    post(ArticlesResource.Comments(slug = created.slug)) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(token.value)
                        setBody(CommentWrapper(NewComment("")))
                    }

                assert(response.status == HttpStatusCode.UnprocessableEntity)
            }
        }
    })
