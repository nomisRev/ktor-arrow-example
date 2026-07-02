package io.github.nomisrev.articles

import arrow.core.raise.either
import io.github.nomisrev.Api
import io.github.nomisrev.Api.Articles
import io.github.nomisrev.Api.Articles.Slug
import io.github.nomisrev.Api.Articles.Slug.Comments
import io.github.nomisrev.Api.Articles.Slug.Comments.Id
import io.github.nomisrev.Api.Articles.Slug.Comments.Id.delete as deleteComment
import io.github.nomisrev.Api.Articles.Slug.Comments.create
import io.github.nomisrev.Api.Articles.Slug.Comments.list
import io.github.nomisrev.Api.Articles.Slug.Favorite
import io.github.nomisrev.Api.Articles.Slug.Favorite.add as favoriteArticle
import io.github.nomisrev.Api.Articles.Slug.Favorite.remove as unfavoriteArticle
import io.github.nomisrev.Api.Articles.Slug.delete as deleteArticle
import io.github.nomisrev.Api.Articles.Slug.get
import io.github.nomisrev.Api.Articles.Slug.update as updateArticle
import io.github.nomisrev.GenericErrorModel
import io.github.nomisrev.articleFixture
import io.github.nomisrev.registerUser
import io.github.nomisrev.userFixture
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.http.HttpStatusCode
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
                val (_, _, userId) = dependencies.registerUser()
                either {
                        val article = articleFixture()
                        val created =
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    userId,
                                    article.title,
                                    article.description,
                                    article.body,
                                    article.tags,
                                )
                            )

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
                    .shouldBeRight()
            }
        }

        "authenticated article reads return viewer specific metadata" {
            withServer { dependencies ->
                val (author, _, authorId) = dependencies.registerUser()
                val (_, viewerToken, viewerId) = dependencies.registerUser()
                val response =
                    either {
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
                            dependencies.articleService.favoriteArticle(
                                io.github.nomisrev.articles.Slug(created.slug),
                                viewerId,
                            )

                            request(Api / Articles / Slug(created.slug) / get) {
                                bearerAuth(viewerToken.value)
                            }
                        }
                        .shouldBeRight()

                val body: SingleArticleResponse = response.bodyOrThrow()
                with(body.article) {
                    assert(favorited)
                    assert(favoritesCount == 1L)
                    assert(this.author.following)
                }
            }
        }

        "can update an article by slug" {
            withServer { dependencies ->
                val (author, token, authorId) = dependencies.registerUser()

                either {
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

                        val response =
                            request(
                                Api / Articles / Slug(created.slug) / updateArticle,
                                ArticleWrapper(UpdateArticle(body = "With two hands")),
                            ) {
                                bearerAuth(token.value)
                            }

                        assert(response.httpResponse.status == HttpStatusCode.OK)
                        val body: SingleArticleResponse = response.bodyOrThrow()
                        assert(body.article.slug == created.slug)
                        assert(body.article.title == created.title)
                        assert(body.article.description == created.description)
                        assert(body.article.body == "With two hands")
                        assert(body.article.author.username == author.username)
                    }
                    .shouldBeRight()
            }
        }

        "favoriting an article updates the response and persisted state" {
            withServer { dependencies ->
                val (_, _, authorId) = dependencies.registerUser()
                val (_, viewerToken) = dependencies.registerUser()

                val (favoriteResponse, readResponse) =
                    either {
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

                            val favoriteResponse =
                                request(
                                    Api / Articles / Slug(created.slug) / Favorite / favoriteArticle
                                ) {
                                    bearerAuth(viewerToken.value)
                                }

                            val readResponse =
                                request(Api / Articles / Slug(created.slug) / get) {
                                    bearerAuth(viewerToken.value)
                                }

                            favoriteResponse to readResponse
                        }
                        .shouldBeRight()

                val favoriteBody: SingleArticleResponse = favoriteResponse.bodyOrThrow()
                with(favoriteBody.article) {
                    assert(favorited)
                    assert(favoritesCount == 1L)
                }

                val readBody: SingleArticleResponse = readResponse.bodyOrThrow()
                with(readBody.article) {
                    assert(favorited)
                    assert(favoritesCount == 1L)
                }
            }
        }

        "favoriting an already favorited article is idempotent" {
            withServer { dependencies ->
                val (_, _, authorId) = dependencies.registerUser()
                val (_, viewerToken) = dependencies.registerUser()

                val (secondFavoriteResponse, readResponse) =
                    either {
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

                            request(
                                Api / Articles / Slug(created.slug) / Favorite / favoriteArticle
                            ) {
                                bearerAuth(viewerToken.value)
                            }

                            val secondFavoriteResponse =
                                request(
                                    Api / Articles / Slug(created.slug) / Favorite / favoriteArticle
                                ) {
                                    bearerAuth(viewerToken.value)
                                }

                            val readResponse =
                                request(Api / Articles / Slug(created.slug) / get) {
                                    bearerAuth(viewerToken.value)
                                }

                            secondFavoriteResponse to readResponse
                        }
                        .shouldBeRight()

                val secondFavoriteBody: SingleArticleResponse = secondFavoriteResponse.bodyOrThrow()
                with(secondFavoriteBody.article) {
                    assert(favorited)
                    assert(favoritesCount == 1L)
                }

                val readBody: SingleArticleResponse = readResponse.bodyOrThrow()
                with(readBody.article) {
                    assert(favorited)
                    assert(favoritesCount == 1L)
                }
            }
        }

        "unfavoriting an article updates the response and persisted state" {
            withServer { dependencies ->
                val (_, _, authorId) = dependencies.registerUser()
                val (_, viewerToken) = dependencies.registerUser()

                val (unfavoriteResponse, readResponse) =
                    either {
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

                            request(
                                Api / Articles / Slug(created.slug) / Favorite / favoriteArticle
                            ) {
                                bearerAuth(viewerToken.value)
                            }

                            val unfavoriteResponse =
                                request(
                                    Api /
                                        Articles /
                                        Slug(created.slug) /
                                        Favorite /
                                        unfavoriteArticle
                                ) {
                                    bearerAuth(viewerToken.value)
                                }

                            val readResponse =
                                request(Api / Articles / Slug(created.slug) / get) {
                                    bearerAuth(viewerToken.value)
                                }

                            unfavoriteResponse to readResponse
                        }
                        .shouldBeRight()

                val unfavoriteBody: SingleArticleResponse = unfavoriteResponse.bodyOrThrow()
                with(unfavoriteBody.article) {
                    assert(!favorited)
                    assert(favoritesCount == 0L)
                }

                val readBody: SingleArticleResponse = readResponse.bodyOrThrow()
                with(readBody.article) {
                    assert(!favorited)
                    assert(favoritesCount == 0L)
                }
            }
        }

        "can get comments for an article by slug when authenticated" {
            withServer { dependencies ->
                val (_, token, userId) = dependencies.registerUser()
                val response =
                    either {
                            val article = articleFixture()
                            val created =
                                dependencies.articleService.createArticle(
                                    CreateArticle(
                                        userId,
                                        article.title,
                                        article.description,
                                        article.body,
                                        article.tags,
                                    )
                                )

                            request(Api / Articles / Slug(created.slug) / Comments / list) {
                                bearerAuth(token.value)
                            }
                        }
                        .shouldBeRight()

                val body = response.bodyOrThrow()
                assert(body.comments == emptyList<Comment>())
            }
        }

        "can get comments for an article when not authenticated" {
            withServer { dependencies ->
                val (_, _, userId) = dependencies.registerUser()
                val response =
                    either {
                            val article = articleFixture()
                            val created =
                                dependencies.articleService.createArticle(
                                    CreateArticle(
                                        userId,
                                        article.title,
                                        article.description,
                                        article.body,
                                        article.tags,
                                    )
                                )

                            request(Api / Articles / Slug(created.slug) / Comments / list)
                        }
                        .shouldBeRight()

                val body = response.bodyOrThrow()
                assert(body.comments == emptyList<Comment>())
            }
        }

        "can list comments for an article when authenticated" {
            withServer { dependencies ->
                val (user, token, userId) = dependencies.registerUser()

                either {
                        val article = articleFixture()
                        val created =
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    userId,
                                    article.title,
                                    article.description,
                                    article.body,
                                    article.tags,
                                )
                            )

                        request(
                            Api / Articles / Slug(created.slug) / Comments / create,
                            CommentWrapper(NewComment("Thank you so much!")),
                        ) {
                            bearerAuth(token.value)
                        }

                        val response =
                            request(Api / Articles / Slug(created.slug) / Comments / list) {
                                bearerAuth(token.value)
                            }

                        val body: MultipleCommentsResponse = response.bodyOrThrow()
                        assert(body.comments.size == 1)
                        val comment = body.comments.single()
                        assert(comment.body == "Thank you so much!")
                        assert(comment.author.username == user.username)
                    }
                    .shouldBeRight()
            }
        }

        "can list comments for an article without authentication" {
            withServer { dependencies ->
                val (user, token, userId) = dependencies.registerUser()

                either {
                        val article = articleFixture()
                        val created =
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    userId,
                                    article.title,
                                    article.description,
                                    article.body,
                                    article.tags,
                                )
                            )

                        request(
                            Api / Articles / Slug(created.slug) / Comments / create,
                            CommentWrapper(NewComment("Thank you so much!")),
                        ) {
                            bearerAuth(token.value)
                        }

                        val response =
                            request(Api / Articles / Slug(created.slug) / Comments / list)

                        val body: MultipleCommentsResponse = response.bodyOrThrow()
                        assert(body.comments.size == 1)
                        val comment = body.comments.single()
                        assert(comment.body == "Thank you so much!")
                        assert(comment.author.username == user.username)
                    }
                    .shouldBeRight()
            }
        }

        "Can add a comment to an article" {
            withServer { dependencies ->
                val (user, token, userId) = dependencies.registerUser()
                either {
                        val comment = "This is a comment ${user.username}"
                        val article = articleFixture()
                        val created =
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    userId,
                                    article.title,
                                    article.description,
                                    article.body,
                                    article.tags,
                                )
                            )

                        val response =
                            request(
                                Api / Articles / Slug(created.slug) / Comments / create,
                                CommentWrapper(NewComment(comment)),
                            ) {
                                bearerAuth(token.value)
                            }

                        val body = response.bodyOrThrow()
                        assert(body.comment.body == comment)
                        assert(body.comment.author.username == user.username)
                    }
                    .shouldBeRight()
            }
        }

        "Can not add a comment to an article with invalid token" {
            withServer { dependencies ->
                val (_, _, userId) = dependencies.registerUser()
                val response =
                    either {
                            val comment = "This is a comment"
                            val article = articleFixture()
                            val created =
                                dependencies.articleService.createArticle(
                                    CreateArticle(
                                        userId,
                                        article.title,
                                        article.description,
                                        article.body,
                                        article.tags,
                                    )
                                )

                            request(
                                Api / Articles / Slug(created.slug) / Comments / create,
                                CommentWrapper(NewComment(comment)),
                            ) {
                                bearerAuth("invalid-token")
                            }
                        }
                        .shouldBeRight()

                assert(response.httpResponse.status == HttpStatusCode.Unauthorized)
            }
        }

        "Can not add a comment to an article with empty body" {
            withServer { dependencies ->
                val (_, token, userId) = dependencies.registerUser()
                val response =
                    either {
                            val article = articleFixture()
                            val created =
                                dependencies.articleService.createArticle(
                                    CreateArticle(
                                        userId,
                                        article.title,
                                        article.description,
                                        article.body,
                                        article.tags,
                                    )
                                )

                            request(
                                Api / Articles / Slug(created.slug) / Comments / create,
                                CommentWrapper(NewComment("")),
                            ) {
                                bearerAuth(token.value)
                            }
                        }
                        .shouldBeRight()

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
            }
        }

        "can delete a comment from an article" {
            withServer { dependencies ->
                val (_, token, userId) = dependencies.registerUser()

                either {
                        val article = articleFixture()
                        val created =
                            dependencies.articleService.createArticle(
                                CreateArticle(
                                    userId,
                                    article.title,
                                    article.description,
                                    article.body,
                                    article.tags,
                                )
                            )

                        val createdComment =
                            request(
                                    Api / Articles / Slug(created.slug) / Comments / create,
                                    CommentWrapper(NewComment("Thank you so much!")),
                                ) {
                                    bearerAuth(token.value)
                                }
                                .bodyOrThrow()

                        val deleteResponse =
                            request(
                                Api /
                                    Articles /
                                    Slug(created.slug) /
                                    Comments /
                                    Id(createdComment.comment.id.toString()) /
                                    deleteComment
                            ) {
                                bearerAuth(token.value)
                            }

                        assert(deleteResponse.httpResponse.status == HttpStatusCode.OK)

                        val listResponse =
                            request(Api / Articles / Slug(created.slug) / Comments / list) {
                                bearerAuth(token.value)
                            }
                        val listed: MultipleCommentsResponse = listResponse.bodyOrThrow()
                        assert(listed.comments.isEmpty())
                    }
                    .shouldBeRight()
            }
        }

        "can delete an article by slug" {
            withServer { dependencies ->
                val (_, token, authorId) = dependencies.registerUser()

                either {
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

                        val deleteResponse =
                            request(Api / Articles / Slug(created.slug) / deleteArticle) {
                                bearerAuth(token.value)
                            }
                        assert(deleteResponse.httpResponse.status == HttpStatusCode.OK)

                        val getResponse = request(Api / Articles / Slug(created.slug) / get)
                        assert(
                            getResponse.httpResponse.status == HttpStatusCode.UnprocessableEntity
                        )
                        assert(
                            getResponse.httpResponse.body<GenericErrorModel>().errors.body ==
                                listOf("Article by slug ${created.slug} not found")
                        )
                    }
                    .shouldBeRight()
            }
        }
    })
