package io.github.nomisrev.articles

import de.infix.testBalloon.framework.core.testSuite
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
import io.github.nomisrev.client
import io.github.nomisrev.createArticle
import io.github.nomisrev.dependencies
import io.github.nomisrev.registerUser
import io.github.nomisrev.testServer
import io.github.nomisrev.tokenAuth
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.ResolvedResource
import opensavvy.spine.api.div
import opensavvy.spine.api.invoke
import opensavvy.spine.client.bodyOrThrow
import opensavvy.spine.client.request

val ArticlesRouteSuite by testSuite {
    testServer("Article by slug not found") {
        val response = client.request(Api / Articles / Slug("slug") / get)

        assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
        assert(
            response.httpResponse.body<GenericErrorModel>().errors.body ==
            ["Article by slug slug not found"],
        )
    }

    testServer("Can get an article by slug") {
        val user = registerUser()
        val created = dependencies.articleService.createArticle(user.userId, articleFixture())

        val response = client.request(Api / Articles / created.slug / get)

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

    testServer("authenticated article reads return viewer specific metadata") {
        val author = registerUser()
        val viewer = registerUser()
        val created = dependencies.articleService.createArticle(author.userId, articleFixture())

        val _ = dependencies.userPersistence.followProfile(author.user.username, viewer.userId)
        val _ = dependencies.articleService.favoriteArticle(created.slug, viewer.userId)

        val response = client.request(Api / Articles / created.slug / get) {
            tokenAuth(viewer.token.value)
        }

        val body: SingleArticleResponse = response.bodyOrThrow()
        with(body.article) {
            assert(favorited)
            assert(favoritesCount == 1L)
            assert(this.author.following)
        }
    }

    testServer("can update an article by slug") {
        val author = registerUser()
        val created = dependencies.articleService.createArticle(author.userId, articleFixture())

        val response = client.request(
            Api / Articles / created.slug / updateArticle,
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
        assert(body.article.author.username == author.user.username.value)
    }

    testServer("favoriting an article updates the response and persisted state") {
        val author = registerUser()
        val viewer = registerUser()
        val created = dependencies.articleService.createArticle(author.userId, articleFixture())

        val favoriteResponse = client
            .request(Api / Articles / created.slug / Favorite / favoriteArticle) {
                tokenAuth(viewer.token.value)
            }

        val readResponse = client.request(Api / Articles / created.slug / get) {
            tokenAuth(viewer.token.value)
        }

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

    testServer("favoriting an already favorited article is idempotent") {
        val author = registerUser()
        val viewer = registerUser()
        val created = dependencies.articleService.createArticle(author.userId, articleFixture())

        val _ = client.request(Api / Articles / created.slug / Favorite / favoriteArticle) {
            tokenAuth(viewer.token.value)
        }

        val secondFavoriteResponse = client
            .request(Api / Articles / created.slug / Favorite / favoriteArticle) {
                tokenAuth(viewer.token.value)
            }

        val readResponse = client.request(Api / Articles / created.slug / get) {
            tokenAuth(viewer.token.value)
        }

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

    testServer("unfavoriting an article updates the response and persisted state") {
        val author = registerUser()
        val viewer = registerUser()
        val created = dependencies.articleService.createArticle(author.userId, articleFixture())

        val _ = client.request(Api / Articles / created.slug / Favorite / favoriteArticle) {
            tokenAuth(viewer.token.value)
        }

        val unfavoriteResponse = client
            .request(Api / Articles / created.slug / Favorite / unfavoriteArticle) {
                tokenAuth(viewer.token.value)
            }

        val readResponse = client.request(Api / Articles / created.slug / get) {
            tokenAuth(viewer.token.value)
        }

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

    testServer("can get comments for an article by slug when authenticated") {
        val user = registerUser()
        val created = dependencies.articleService.createArticle(user.userId, articleFixture())

        val response = client.request(Api / Articles / created.slug / Comments / list) {
            tokenAuth(user.token.value)
        }

        val body = response.bodyOrThrow()
        assert(body.comments == emptyList<Comment>())
    }

    testServer("can get comments for an article when not authenticated") {
        val (userId) = registerUser()
        val created = dependencies.articleService.createArticle(userId, articleFixture())

        val response = client.request(Api / Articles / created.slug / Comments / list)

        val body = response.bodyOrThrow()
        assert(body.comments == emptyList<Comment>())
    }

    testServer("can list comments for an article when authenticated") {
        val (user, token, userId) = registerUser()
        val created = dependencies.articleService.createArticle(userId, articleFixture())

        val _ = client.request(
            Api / Articles / created.slug / Comments / create,
            CommentWrapper(NewComment("Thank you so much!")),
        ) {
            tokenAuth(token.value)
        }

        val response = client.request(Api / Articles / created.slug / Comments / list) {
            tokenAuth(token.value)
        }

        val body: MultipleCommentsResponse = response.bodyOrThrow()
        assert(body.comments.size == 1)
        val comment = body.comments.single()
        assert(comment.body == "Thank you so much!")
        assert(comment.author.username == user.username.value)
    }

    testServer("can list comments for an article without authentication") {
        val (user, token, userId) = registerUser()
        val created = dependencies.articleService.createArticle(userId, articleFixture())

        val _ = client.request(
            Api / Articles / created.slug / Comments / create,
            CommentWrapper(NewComment("Thank you so much!")),
        ) {
            tokenAuth(token.value)
        }

        val response = client.request(Api / Articles / created.slug / Comments / list)

        val body: MultipleCommentsResponse = response.bodyOrThrow()
        assert(body.comments.size == 1)
        val comment = body.comments.single()
        assert(comment.body == "Thank you so much!")
        assert(comment.author.username == user.username.value)
    }

    testServer("Can add a comment to an article") {
        val (user, token, userId) = registerUser()
        val comment = "This is a comment ${user.username}"
        val created = dependencies.articleService.createArticle(userId, articleFixture())

        val response = client.request(
            Api / Articles / created.slug / Comments / create,
            CommentWrapper(NewComment(comment)),
        ) {
            tokenAuth(token.value)
        }

        val body = response.bodyOrThrow()
        assert(body.comment.body == comment)
        assert(body.comment.author.username == user.username.value)
    }

    testServer("Can not add a comment to an article with invalid token") {
        val (userId) = registerUser()
        val created = dependencies.articleService.createArticle(userId, articleFixture())

        val response = client.request(
            Api / Articles / created.slug / Comments / create,
            CommentWrapper(NewComment("This is a comment")),
        ) {
            tokenAuth("invalid-token")
        }

        assert(response.httpResponse.status == HttpStatusCode.Unauthorized)
    }

    testServer("Can not add a comment to an article with empty body") {
        val user = registerUser()
        val created = dependencies.articleService.createArticle(user.userId, articleFixture())

        val response = client.request(
            Api / Articles / created.slug / Comments / create,
            CommentWrapper(NewComment("")),
        ) {
            tokenAuth(user.token.value)
        }

        assert(response.httpResponse.status == HttpStatusCode.UnprocessableEntity)
    }

    testServer("can delete a comment from an article") {
        val user = registerUser()
        val created = dependencies.articleService.createArticle(user.userId, articleFixture())

        val createdComment = client
            .request(
                Api / Articles / created.slug / Comments / create,
                CommentWrapper(NewComment("Thank you so much!")),
            ) {
                tokenAuth(user.token.value)
            }
            .bodyOrThrow()

        val deleteResponse = client.request(
            Api /
            Articles /
            created.slug /
            Comments /
            Id(createdComment.comment.id.toString()) /
            deleteComment,
        ) {
            tokenAuth(user.token.value)
        }

        assert(deleteResponse.httpResponse.status == HttpStatusCode.OK)

        val listResponse = client.request(Api / Articles / created.slug / Comments / list) {
            tokenAuth(user.token.value)
        }
        val listed: MultipleCommentsResponse = listResponse.bodyOrThrow()
        assert(listed.comments.isEmpty())
    }

    testServer("can delete an article by slug") {
        val user = registerUser()
        val created = dependencies.articleService.createArticle(user.userId, articleFixture())

        val deleteResponse = client.request(Api / Articles / created.slug / deleteArticle) {
            tokenAuth(user.token.value)
        }
        assert(deleteResponse.httpResponse.status == HttpStatusCode.OK)

        val getResponse = client.request(Api / Articles / created.slug / get)
        assert(getResponse.httpResponse.status == HttpStatusCode.UnprocessableEntity)
        assert(
            getResponse.httpResponse.body<GenericErrorModel>().errors.body ==
            ["Article by slug ${created.slug.value} not found"],
        )
    }
}

operator fun ResolvedResource<Articles>.div(
    slug: io.github.nomisrev.articles.Slug,
): ResolvedResource<Slug> = div(Slug(slug.value))
