@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.articles

import io.github.nomisrev.Api
import io.github.nomisrev.auth.JwtService
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.auth.optionalJwtAuth
import io.github.nomisrev.profiles.Profile
import io.github.nomisrev.route
import io.github.nomisrev.users.UserId
import io.github.nomisrev.users.UserService
import io.github.nomisrev.validate
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import java.time.OffsetDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import opensavvy.spine.server.respond

@Serializable data class ArticleWrapper<T : Any>(val article: T)

@Serializable
data class Article(
    val articleId: Long,
    val slug: String,
    val title: String,
    val description: String,
    val body: String,
    val author: Profile,
    val favorited: Boolean,
    val favoritesCount: Long,
    @Serializable(with = OffsetDateTimeIso8601Serializer::class) val createdAt: OffsetDateTime,
    @Serializable(with = OffsetDateTimeIso8601Serializer::class) val updatedAt: OffsetDateTime,
    val tagList: List<String>,
)

@Serializable data class SingleArticleResponse(val article: Article)

@Serializable
data class MultipleArticlesResponse(val articles: List<Article>, val articlesCount: Int)

@JvmInline @Serializable value class FeedOffset(val offset: Int)

@JvmInline @Serializable value class FeedLimit(val limit: Int)

@Serializable data class CommentWrapper<T : Any>(val comment: T)

@Serializable data class NewComment(val body: String)

@Serializable data class SingleCommentResponse(val comment: Comment)

@Serializable
data class Comment(
    val commentId: Long,
    @Serializable(with = OffsetDateTimeIso8601Serializer::class) val createdAt: OffsetDateTime,
    @Serializable(with = OffsetDateTimeIso8601Serializer::class) val updatedAt: OffsetDateTime,
    val body: String,
    val author: Profile,
)

@Serializable data class MultipleCommentsResponse(val comments: List<Comment>)

@Serializable
data class NewArticle(
    val title: String,
    val description: String,
    val body: String,
    val tagList: List<String> = emptyList(),
)

@Serializable
data class UpdateArticle(
    val title: String? = null,
    val description: String? = null,
    val body: String? = null,
)

// Validation input models kept separate from Spine resources so validation tests remain simple.
class ArticleResource {
    data class Feed(val offsetParam: Int = 0, val limitParam: Int = 20)
}

data class ArticlesResource(
    val author: String? = null,
    val favorited: String? = null,
    val tag: String? = null,
    val offsetParam: Int = 0,
    val limitParam: Int = 20,
) {
    data class Slug(val slug: String) {
        data class Favorite(val parent: Slug)
    }

    data class Comments(val slug: String) {
        data class Id(val id: Long)
    }
}

private fun ApplicationCall.articlesResource(): ArticlesResource =
    ArticlesResource(
        author = request.queryParameters["author"],
        favorited = request.queryParameters["favorited"],
        tag = request.queryParameters["tag"],
        offsetParam = request.queryParameters["offsetParam"]?.toIntOrNull() ?: 0,
        limitParam = request.queryParameters["limitParam"]?.toIntOrNull() ?: 20,
    )

private fun ApplicationCall.feedResource(): ArticleResource.Feed =
    ArticleResource.Feed(
        offsetParam = request.queryParameters["offsetParam"]?.toIntOrNull() ?: 0,
        limitParam = request.queryParameters["limitParam"]?.toIntOrNull() ?: 20,
    )

fun Route.articleRoutes(articleService: ArticleService, jwtService: JwtService) {
    route(Api.Articles.list) {
        optionalJwtAuth(jwtService) { context ->
            val input = call.articlesResource().validate(context?.userId)
            val articles = articleService.getAllArticles(input)
            respond(articles)
        }
    }

    route(Api.Article.feed) {
        jwtAuth(jwtService) { (_, userId) ->
            val input = call.feedResource().validate(userId)
            val feed = articleService.getUserFeed(input)
            respond(feed)
        }
    }

    route(Api.Articles.Slug.get) {
        optionalJwtAuth(jwtService) { context ->
            val article =
                articleService.getArticleBySlug(
                    Slug(idOf(Api.Articles.Slug)),
                    context?.userId,
                )

            respond(SingleArticleResponse(article))
        }
    }

    route(Api.Articles.Slug.update) {
        jwtAuth(jwtService) { (_, userId) ->
            val input =
                UpdateArticleInput(
                    slug = Slug(idOf(Api.Articles.Slug)),
                    userId = userId,
                    title = body.article.title,
                    description = body.article.description,
                    body = body.article.body,
                )
            val updatedArticle = articleService.updateArticle(input)

            respond(
                ArticleResponse(
                    slug = updatedArticle.slug,
                    title = updatedArticle.title,
                    description = updatedArticle.description,
                    body = updatedArticle.body,
                    author = updatedArticle.author,
                    favorited = updatedArticle.favorited,
                    favoritesCount = updatedArticle.favoritesCount,
                    createdAt = updatedArticle.createdAt,
                    updatedAt = updatedArticle.updatedAt,
                    tagList = updatedArticle.tagList,
                )
            )
        }
    }

    route(Api.Articles.Slug.delete) {
        jwtAuth(jwtService) { (_, userId) ->
            articleService.deleteArticle(Slug(idOf(Api.Articles.Slug)), userId)
            respond(code = HttpStatusCode.OK)
        }
    }

    route(Api.Articles.Slug.Favorite.add) {
        jwtAuth(jwtService) { (_, userId) ->
            val article = articleService.favoriteArticle(Slug(idOf(Api.Articles.Slug)), userId)
            respond(SingleArticleResponse(article))
        }
    }

    route(Api.Articles.Slug.Favorite.remove) {
        jwtAuth(jwtService) { (_, userId) ->
            val article = articleService.unfavoriteArticle(Slug(idOf(Api.Articles.Slug)), userId)
            respond(SingleArticleResponse(article))
        }
    }

    route(Api.Articles.create) {
        jwtAuth(jwtService) { (_, userId) ->
            val article = body.article.validate()
            val created =
                articleService.createArticle(
                    CreateArticle(
                        userId,
                        article.title,
                        article.description,
                        article.body,
                        article.tagList.toSet(),
                    )
                )
            respond(SingleArticleResponse(created), HttpStatusCode.Created)
        }
    }
}

fun Route.commentRoutes(
    userService: UserService,
    articleService: ArticleService,
    jwtService: JwtService,
) {
    route(Api.Articles.Slug.Comments.create) {
        jwtAuth(jwtService) { (_, userId) ->
            val commentBody = body.comment.validate()
            val comments =
                articleService.insertCommentForArticleSlug(
                    slug = Slug(idOf(Api.Articles.Slug)),
                    userId = userId,
                    comment = commentBody.body,
                )
            val userProfile = userService.getUser(UserId(comments.author))

            respond(
                SingleCommentResponse(
                    Comment(
                        commentId = comments.id,
                        createdAt = comments.createdAt,
                        updatedAt = comments.updatedAt,
                        body = comments.body,
                        author =
                            Profile(
                                username = userProfile.username,
                                bio = userProfile.bio,
                                image = userProfile.image,
                                following = false,
                            ),
                    )
                )
            )
        }
    }

    route(Api.Articles.Slug.Comments.list) {
        jwtAuth(jwtService) { _ ->
            val comments = articleService.getCommentsForSlug(Slug(idOf(Api.Articles.Slug)))
            respond(MultipleCommentsResponse(comments))
        }
    }

    route(Api.Articles.Slug.Comments.Id.delete) {
        jwtAuth(jwtService) { (_, userId) ->
            articleService.deleteComment(
                commentId = idOf(Api.Articles.Slug.Comments.Id).toLong(),
                userId = userId,
            )
            respond(code = HttpStatusCode.OK)
        }
    }
}

@Serializable
data class ArticleResponse(
    val slug: String,
    val title: String,
    val description: String,
    val body: String,
    val author: Profile,
    val favorited: Boolean,
    val favoritesCount: Long,
    @Serializable(with = OffsetDateTimeIso8601Serializer::class) val createdAt: OffsetDateTime,
    @Serializable(with = OffsetDateTimeIso8601Serializer::class) val updatedAt: OffsetDateTime,
    val tagList: List<String>,
)

private object OffsetDateTimeIso8601Serializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OffsetDateTime =
        OffsetDateTime.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        encoder.encodeString(value.toString())
    }
}
