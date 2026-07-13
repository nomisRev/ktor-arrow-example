package io.github.nomisrev.articles

import de.infix.testBalloon.framework.core.testSuite
import io.github.nomisrev.Api
import io.github.nomisrev.Api.Articles
import io.github.nomisrev.Api.Articles.create
import io.github.nomisrev.Api.Articles.feed
import io.github.nomisrev.Api.Articles.list
import io.github.nomisrev.GenericErrorModel
import io.github.nomisrev.articleFixture
import io.github.nomisrev.client
import io.github.nomisrev.createArticle
import io.github.nomisrev.dependencies
import io.github.nomisrev.registerUser
import io.github.nomisrev.testServer
import io.github.nomisrev.tokenAuth
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.div
import opensavvy.spine.client.bodyOrThrow
import opensavvy.spine.client.request

val ArticleRouteSuite by testSuite {
    testServer("Check for empty feed") {
        val (token) = registerUser()

        val response =
            client.request(
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

    testServer("ٰValidate correct both offset and limit value") {
        val (token) = registerUser()

        val response =
            client.request(
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

    testServer("ٰValidate wrong offset value") {
        val (token) = registerUser()

        val response =
            client.request(
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

    testServer("ٰValidate wrong limit value") {
        val (token) = registerUser()

        val response =
            client.request(
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

    testServer("ٰValidate wrong both limit and value") {
        val (token) = registerUser()

        val response =
            client.request(
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

    testServer("article list accepts OpenAPI offset and limit query parameters") {
        val (userId) = registerUser()
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
            client.request(
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

    testServer("article list returns viewer specific metadata") {
        val author = registerUser()
        val viewer = registerUser()
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

        val _ = dependencies.userPersistence.followProfile(author.user.username, viewer.userId)
        val _ = dependencies.articleService.favoriteArticle(Slug(created.slug), viewer.userId)

        val response =
            client.request(
                endpoint = Api / Articles / list,
                parameters = { this.author = author.user.username },
            ) {
                tokenAuth(viewer.token.value)
            }

        val body = response.bodyOrThrow().articles.singleOrNull()
        assert(body?.slug == created.slug)
        assert(body?.favorited ?: false)
        assert(body?.favoritesCount == 1L)
        assert(body?.author?.following ?: false)
    }

    testServer("feed returns articles from followed authors") {
        val reader = registerUser()
        val followed = registerUser()
        val unrelated = registerUser()

        val _ = dependencies.userPersistence.followProfile(followed.user.username, reader.userId)

        val createdFollowedArticle =
            dependencies.articleService.createArticle(followed.userId, articleFixture())
        val _ = dependencies.articleService.createArticle(unrelated.userId, articleFixture())

        val response =
            client.request(
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

    testServer("article list filters by author") {
        val author = registerUser()
        val otherAuthor = registerUser()

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
        val _ =
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
            client.request(
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

    testServer("article list filters by author when authenticated") {
        val author = registerUser()
        val viewer = registerUser()

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

        val _ = dependencies.userPersistence.followProfile(author.user.username, viewer.userId)

        val response =
            client.request(
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

    testServer("article list filters by tag") {
        val (userId) = registerUser()

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

        val _ =
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
            client.request(
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

    testServer("article list filters by favorited username") {
        val author = registerUser()
        val viewer = registerUser()

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

        val _ = dependencies.articleService.favoriteArticle(Slug(created.slug), viewer.userId)

        val response =
            client.request(
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

    testServer("article list filters by favorited username when authenticated") {
        val author = registerUser()
        val viewer = registerUser()

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

        val _ = dependencies.articleService.favoriteArticle(Slug(created.slug), viewer.userId)

        val response =
            client.request(
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

    testServer("create article with tags") {
        val (user, token) = registerUser()
        val article = articleFixture()

        val response =
            client.request(
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

    testServer("article without tags") {
        val (user, token) = registerUser()
        val article = articleFixture()

        val response =
            client.request(
                Api / Articles / create,
                ArticleWrapper(
                    NewArticle(article.title, article.description, article.body, emptyList())
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

    testServer("body cannot be empty") {
        val (token) = registerUser()
        val article = articleFixture()

        val response =
            client.request(
                Api / Articles / create,
                ArticleWrapper(NewArticle(article.title, article.description, "", emptyList())),
            ) {
                tokenAuth(token.value)
            }

        assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
    }

    testServer("description cannot be empty") {
        val (token) = registerUser()
        val article = articleFixture()

        val response =
            client.request(
                Api / Articles / create,
                ArticleWrapper(NewArticle(article.title, "", article.body, emptyList())),
            ) {
                tokenAuth(token.value)
            }

        assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
    }

    testServer("title cannot be empty") {
        val (token) = registerUser()
        val article = articleFixture()

        val response =
            client.request(
                Api / Articles / create,
                ArticleWrapper(NewArticle("", article.description, article.body, emptyList())),
            ) {
                tokenAuth(token.value)
            }

        assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
    }

    testServer("Unauthorized user cannot create article") {
        val article = articleFixture()
        val response =
            client.request(
                Api / Articles / create,
                ArticleWrapper(
                    NewArticle(article.title, article.description, article.body, emptyList())
                ),
            )

        assert(response.httpResponse.status == HttpStatusCode.Unauthorized)
    }
}
