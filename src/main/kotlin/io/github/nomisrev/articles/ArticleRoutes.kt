@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.articles

import arrow.core.raise.context.Raise
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.accumulating
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.withError
import io.github.nomisrev.Api
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.MissingParameter
import io.github.nomisrev.auth.JwtConfig
import io.github.nomisrev.auth.JwtContext
import io.github.nomisrev.auth.authenticateWith
import io.github.nomisrev.auth.principal
import io.github.nomisrev.profiles.Profile
import io.github.nomisrev.route
import io.github.nomisrev.users.UserId
import io.github.nomisrev.users.UserService
import io.github.nomisrev.validFeedLimit
import io.github.nomisrev.validFeedOffset
import io.github.nomisrev.validate
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import java.time.OffsetDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import opensavvy.spine.api.ParameterStorage
import opensavvy.spine.api.Parameters
import opensavvy.spine.api.getValue
import opensavvy.spine.api.provideDelegate
import opensavvy.spine.api.setValue
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
    val id: Long,
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

class ArticlesParameters(data: ParameterStorage) : Parameters(data) {
    var author: String? by parameter()
    var favorited: String? by parameter()
    var tag: String? by parameter()
    var offset: Int by parameter(default = 0)
    var limit: Int by parameter(default = 20)

    context(_: Raise<IncorrectInput>)
    fun validate(currentUserId: UserId?): GetArticles =
        withError(::IncorrectInput) {
            accumulate {
                val offset by accumulating { offset.validFeedOffset() }
                val limit by accumulating { limit.validFeedLimit() }
                GetArticles(
                    limit = limit.limit,
                    offset = offset.offset,
                    author = author,
                    favorited = favorited,
                    tag = tag,
                    currentUserId = currentUserId,
                )
            }
        }
}

class FeedParameters(data: ParameterStorage) : Parameters(data) {
    var offset: Int by parameter(default = 0)
    var limit: Int by parameter(default = 20)

    context(_: Raise<IncorrectInput>)
    fun validate(userId: UserId): GetFeed =
        withError(::IncorrectInput) {
            accumulate {
                val offset by accumulating { offset.validFeedOffset() }
                val limit by accumulating { limit.validFeedLimit() }
                GetFeed(userId, limit.limit, offset.offset)
            }
        }
}

fun Route.articleRoutes(articleService: ArticleService, jwtService: JwtConfig<JwtContext>) {
    authenticateWith(jwtService.orAnonymous()) {
        route(Api.Articles.list) {
            val input = parameters.validate(call.principal?.userId)
            val articles = articleService.getAllArticles(input)
            respond(articles)
        }

        route(Api.Articles.Slug.get) {
            val article =
                articleService.getArticleBySlug(
                    Slug(idOf(Api.Articles.Slug)),
                    call.principal?.userId,
                )

            respond(SingleArticleResponse(article))
        }
    }

    authenticateWith(jwtService) {
        route(Api.Articles.feed) {
            val input = parameters.validate(call.principal.userId)
            val feed = articleService.getUserFeed(input)
            respond(feed)
        }

        route(Api.Articles.Slug.update) {
            val input =
                UpdateArticleInput(
                    slug = Slug(idOf(Api.Articles.Slug)),
                    userId = call.principal.userId,
                    title = body.article.title,
                    description = body.article.description,
                    body = body.article.body,
                )
            val updatedArticle = articleService.updateArticle(input)

            respond(SingleArticleResponse(updatedArticle))
        }

        route(Api.Articles.Slug.delete) {
            articleService.deleteArticle(Slug(idOf(Api.Articles.Slug)), call.principal.userId)
            respond(code = HttpStatusCode.OK)
        }

        route(Api.Articles.Slug.Favorite.add) {
            val article =
                articleService.favoriteArticle(Slug(idOf(Api.Articles.Slug)), call.principal.userId)
            respond(SingleArticleResponse(article))
        }

        route(Api.Articles.Slug.Favorite.remove) {
            val article =
                articleService.unfavoriteArticle(
                    Slug(idOf(Api.Articles.Slug)),
                    call.principal.userId,
                )
            respond(SingleArticleResponse(article))
        }

        route(Api.Articles.create) {
            val article = body.article.validate()
            val created =
                articleService.createArticle(
                    CreateArticle(
                        call.principal.userId,
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
    jwtService: JwtConfig<JwtContext>,
) {
    route(Api.Articles.Slug.Comments.list) {
        val comments = articleService.getCommentsForSlug(Slug(idOf(Api.Articles.Slug)))
        respond(MultipleCommentsResponse(comments))
    }

    authenticateWith(jwtService) {
        route(Api.Articles.Slug.Comments.create) {
            val commentBody = body.comment.validate()
            val comments =
                articleService.insertCommentForArticleSlug(
                    slug = Slug(idOf(Api.Articles.Slug)),
                    userId = call.principal.userId,
                    comment = commentBody.body,
                )
            val userProfile = userService.getUser(UserId(comments.author))

            respond(
                SingleCommentResponse(
                    Comment(
                        id = comments.id,
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

        route(Api.Articles.Slug.Comments.Id.delete) {
            val commentId =
                ensureNotNull(idOf(Api.Articles.Slug.Comments.Id).toLongOrNull()) {
                    MissingParameter("commentId must be a number")
                }
            articleService.deleteComment(commentId = commentId, userId = call.principal.userId)
            respond(code = HttpStatusCode.OK)
        }
    }
}

private object OffsetDateTimeIso8601Serializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OffsetDateTime =
        OffsetDateTime.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        encoder.encodeString(value.toString())
    }
}
