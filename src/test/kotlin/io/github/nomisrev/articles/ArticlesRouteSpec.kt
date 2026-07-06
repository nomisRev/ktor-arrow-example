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
import io.github.nomisrev.tokenAuth
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.call.body
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
                        ["Article by slug slug not found"]
                )
            }
        }

        "Can get an article by slug" {
            withServer { dependencies ->
                val user = dependencies.registerUser()
                either {
                    val article = articleFixture()
                    val created =
                        dependencies.articleService.createArticle(
                            CreateArticle(
                                user.userId,
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
                val author = dependencies.registerUser()
                val viewer = dependencies.registerUser()
                val response = either {
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

                    val _ =
                        dependencies.userPersistence.followProfile(
                            author.user.username,
                            viewer.userId,
                        )
                    val _ =
                        dependencies.articleService.favoriteArticle(
                            io.github.nomisrev.articles.Slug(created.slug),
                            viewer.userId,
                        )

                    request(Api / Articles / Slug(created.slug) / get) {
                        tokenAuth(viewer.token.value)
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
                val author = dependencies.registerUser()

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

                    val response =
                        request(
                            Api / Articles / Slug(created.slug) / updateArticle,
                            ArticleWrapper(UpdateArticle(body = "With two hands")),
                        ) {
                            tokenAuth(author.token.value)
                        }

                    assert(response.httpResponse.status == HttpStatusCode.OK)
                    val body: SingleArticleResponse = response.bodyOrThrow()
                    assert(body.article.slug == created.slug)
                    assert(body.article.title == created.title)
                    assert(body.article.description == created.description)
                    assert(body.article.body == "With two hands")
                    assert(body.article.author.username == author.user.username)
                }
                    .shouldBeRight()
            }
        }

        "favoriting an article updates the response and persisted state" {
            withServer { dependencies ->
                val author = dependencies.registerUser()
                val viewer = dependencies.registerUser()

                val [favoriteResponse, readResponse] =
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

                        val favoriteResponse =
                            request(
                                Api / Articles / Slug(created.slug) / Favorite / favoriteArticle
                            ) {
                                tokenAuth(viewer.token.value)
                            }

                        val readResponse =
                            request(Api / Articles / Slug(created.slug) / get) {
                                tokenAuth(viewer.token.value)
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
                val author = dependencies.registerUser()
                val viewer = dependencies.registerUser()

                val [secondFavoriteResponse, readResponse] =
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

                        val _ =
                            request(
                                Api / Articles / Slug(created.slug) / Favorite / favoriteArticle
                            ) {
                                tokenAuth(viewer.token.value)
                            }

                        val secondFavoriteResponse =
                            request(
                                Api / Articles / Slug(created.slug) / Favorite / favoriteArticle
                            ) {
                                tokenAuth(viewer.token.value)
                            }

                        val readResponse =
                            request(Api / Articles / Slug(created.slug) / get) {
                                tokenAuth(viewer.token.value)
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
                val author = dependencies.registerUser()
                val viewer = dependencies.registerUser()

                val [unfavoriteResponse, readResponse] =
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

                        val _ =
                            request(
                                Api / Articles / Slug(created.slug) / Favorite / favoriteArticle
                            ) {
                                tokenAuth(viewer.token.value)
                            }

                        val unfavoriteResponse =
                            request(
                                Api / Articles / Slug(created.slug) / Favorite / unfavoriteArticle
                            ) {
                                tokenAuth(viewer.token.value)
                            }

                        val readResponse =
                            request(Api / Articles / Slug(created.slug) / get) {
                                tokenAuth(viewer.token.value)
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
                val user = dependencies.registerUser()
                val response = either {
                    val article = articleFixture()
                    val created =
                        dependencies.articleService.createArticle(
                            CreateArticle(
                                user.userId,
                                article.title,
                                article.description,
                                article.body,
                                article.tags,
                            )
                        )

                    request(Api / Articles / Slug(created.slug) / Comments / list) {
                        tokenAuth(user.token.value)
                    }
                }
                    .shouldBeRight()

                val body = response.bodyOrThrow()
                assert(body.comments == emptyList<Comment>())
            }
        }

        "can get comments for an article when not authenticated" {
            withServer { dependencies ->
                val (userId) = dependencies.registerUser()
                val response = either {
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
                        tokenAuth(token.value)
                    }

                    val response =
                        request(Api / Articles / Slug(created.slug) / Comments / list) {
                            tokenAuth(token.value)
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
                        tokenAuth(token.value)
                    }

                    val response = request(Api / Articles / Slug(created.slug) / Comments / list)

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
                            tokenAuth(token.value)
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
                val (userId) = dependencies.registerUser()
                val response = either {
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
                        tokenAuth("invalid-token")
                    }
                }
                    .shouldBeRight()

                assert(response.httpResponse.status == HttpStatusCode.Unauthorized)
            }
        }

        "Can not add a comment to an article with empty body" {
            withServer { dependencies ->
                val user = dependencies.registerUser()
                val response = either {
                    val article = articleFixture()
                    val created =
                        dependencies.articleService.createArticle(
                            CreateArticle(
                                user.userId,
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
                        tokenAuth(user.token.value)
                    }
                }
                    .shouldBeRight()

                assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
            }
        }

        "can delete a comment from an article" {
            withServer { dependencies ->
                val user = dependencies.registerUser()

                either {
                    val article = articleFixture()
                    val created =
                        dependencies.articleService.createArticle(
                            CreateArticle(
                                user.userId,
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
                                tokenAuth(user.token.value)
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
                            tokenAuth(user.token.value)
                        }

                    assert(deleteResponse.httpResponse.status == HttpStatusCode.OK)

                    val listResponse =
                        request(Api / Articles / Slug(created.slug) / Comments / list) {
                            tokenAuth(user.token.value)
                        }
                    val listed: MultipleCommentsResponse = listResponse.bodyOrThrow()
                    assert(listed.comments.isEmpty())
                }
                    .shouldBeRight()
            }
        }

        "can delete an article by slug" {
            withServer { dependencies ->
                val user = dependencies.registerUser()

                either {
                    val article = articleFixture()
                    val created =
                        dependencies.articleService.createArticle(
                            CreateArticle(
                                user.userId,
                                article.title,
                                article.description,
                                article.body,
                                article.tags,
                            )
                        )

                    val deleteResponse =
                        request(Api / Articles / Slug(created.slug) / deleteArticle) {
                            tokenAuth(user.token.value)
                        }
                    assert(deleteResponse.httpResponse.status == HttpStatusCode.OK)

                    val getResponse = request(Api / Articles / Slug(created.slug) / get)
                    assert(getResponse.httpResponse.status == HttpStatusCode.UnprocessableEntity)
                    assert(
                        getResponse.httpResponse.body<GenericErrorModel>().errors.body ==
                            ["Article by slug ${created.slug} not found"]
                    )
                }
                    .shouldBeRight()
            }
        }
    })
