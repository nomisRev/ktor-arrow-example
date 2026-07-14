@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.articles

import arrow.core.nonEmptyListOf
import arrow.core.raise.context.Raise
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.accumulating
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.mapOrAccumulate
import arrow.core.raise.context.withError
import io.github.nomisrev.Api
import io.github.nomisrev.Body
import io.github.nomisrev.Description
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.InvalidBody
import io.github.nomisrev.InvalidFeedLimit
import io.github.nomisrev.InvalidFeedOffset
import io.github.nomisrev.InvalidField
import io.github.nomisrev.InvalidTag
import io.github.nomisrev.MissingParameter
import io.github.nomisrev.Title
import io.github.nomisrev.Username
import io.github.nomisrev.auth.JwtConfig
import io.github.nomisrev.auth.JwtContext
import io.github.nomisrev.auth.authenticateWith
import io.github.nomisrev.auth.principal
import io.github.nomisrev.notBlank
import io.github.nomisrev.profiles.Profile
import io.github.nomisrev.route
import io.github.nomisrev.users.UserId
import io.github.nomisrev.users.UserService
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
    val slug: Slug,
    val title: String,
    val description: String,
    val body: String,
    val author: Profile,
    val favorited: Boolean,
    val favoritesCount: Long,
    @Serializable(with = OffsetDateTimeIso8601Serializer::class) val createdAt: OffsetDateTime,
    @Serializable(with = OffsetDateTimeIso8601Serializer::class) val updatedAt: OffsetDateTime,
    val tagList: Set<String>,
)

@Serializable data class SingleArticleResponse(val article: Article)

@Serializable
data class MultipleArticlesResponse(val articles: List<Article>, val articlesCount: Int)

@JvmInline
value class FeedOffset private constructor(val value: Long) {
    companion object {
        private const val MIN_FEED_OFFSET = 0

        context(_: Raise<InvalidFeedOffset>)
        operator fun invoke(offset: Int): FeedOffset =
            withError<InvalidFeedOffset, String, FeedOffset>({
                InvalidFeedOffset(nonEmptyListOf(it))
            }) {
                ensure(offset >= MIN_FEED_OFFSET) {
                    "too small, minimum is $MIN_FEED_OFFSET, and found $offset"
                }
                FeedOffset(offset.toLong())
            }
    }
}

@JvmInline
value class FeedLimit private constructor(val value: Long) {
    companion object {
        private const val MIN_FEED_LIMIT = 1

        context(_: Raise<InvalidFeedLimit>)
        // TODO: Check inference problem and report to YouTrack.
        //  IntelliJ suggest it's not needed but when removed report ambuigity.
        //  Context parameter inference should infer OtherError == String, this should disambiguate
        operator fun invoke(limit: Int): FeedLimit =
            withError<InvalidFeedLimit, String, FeedLimit>(::InvalidFeedLimit) {
                ensure(limit >= MIN_FEED_LIMIT) { "too small, minimum is 1, and found $limit" }
                FeedLimit(limit.toLong())
            }
    }
}

@Serializable data class CommentWrapper<T : Any>(val comment: T)

@Serializable
data class NewComment(val body: String) {
    context(_: Raise<IncorrectInput>)
    fun toCreateComment(slug: Slug, userId: UserId): CreateComment =
        withError({ IncorrectInput(nonEmptyListOf(it)) }) {
            val value = body.trim()
            ensure(value.isNotBlank()) { InvalidBody("Cannot be blank") }
            return CreateComment(userId = userId, slug = slug, body = value)
        }
}

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
) {
    context(_: Raise<IncorrectInput>)
    fun toCreateArticle(userId: UserId): CreateArticle =
        withError(::IncorrectInput) {
            accumulate {
                val title by accumulating { Title(title) }
                val description by accumulating { Description(description) }
                val body by accumulating { Body(body) }
                val tags by accumulating { tagList.validTags() }
                CreateArticle(
                    userId = userId,
                    title = title,
                    description = description,
                    body = body,
                    tags = tags,
                )
            }
        }

    context(_: Raise<InvalidField>)
    private fun List<String>.validTags(): Set<String> =
        withError(::InvalidTag) { mapOrAccumulate { it.trim().notBlank() }.toSet() }
}

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
    fun toGetArticles(currentUserId: UserId?): GetArticles =
        withError(::IncorrectInput) {
            accumulate {
                val offset by accumulating { FeedOffset(offset) }
                val limit by accumulating { FeedLimit(limit) }
                val author by accumulating { author?.let { Username(it) } }
                val favorited by accumulating { favorited?.let { Username(it) } }

                GetArticles(
                    limit = limit,
                    offset = offset,
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
    fun toGetFeed(userId: UserId): GetFeed =
        withError(::IncorrectInput) {
            accumulate {
                val offset by accumulating { FeedOffset(offset) }
                val limit by accumulating { FeedLimit(limit) }
                GetFeed(userId, limit, offset)
            }
        }
}

fun Route.articleRoutes(articleService: ArticleService, jwtService: JwtConfig<JwtContext>) {
    authenticateWith(jwtService.orAnonymous()) {
        route(Api.Articles.list) {
            val input = parameters.toGetArticles(call.principal?.userId)
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
            val input = parameters.toGetFeed(call.principal.userId)
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
            val created =
                articleService.createArticle(body.article.toCreateArticle(call.principal.userId))
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
            val comments =
                articleService.insertComment(
                    body.comment.toCreateComment(
                        slug = Slug(idOf(Api.Articles.Slug)),
                        userId = call.principal.userId,
                    )
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
                                username = userProfile.username.value,
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
