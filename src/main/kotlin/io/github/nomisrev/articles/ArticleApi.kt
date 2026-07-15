package io.github.nomisrev.articles

import arrow.core.nonEmptyListOf
import arrow.core.raise.context.Raise
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.accumulating
import arrow.core.raise.context.mapOrAccumulate
import arrow.core.raise.context.withError
import io.github.nomisrev.Body
import io.github.nomisrev.Description
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.InvalidField
import io.github.nomisrev.InvalidTag
import io.github.nomisrev.Title
import io.github.nomisrev.Username
import io.github.nomisrev.notBlank
import io.github.nomisrev.profiles.Profile
import io.github.nomisrev.users.UserId
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import opensavvy.spine.api.ParameterStorage
import opensavvy.spine.api.Parameters
import opensavvy.spine.api.getValue
import opensavvy.spine.api.provideDelegate
import opensavvy.spine.api.setValue

@Serializable
data class ArticleWrapper<T : Any>(val article: T)

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
    @Serializable(with = OffsetDateTimeIso8601Serializer::class)
    val createdAt: OffsetDateTime,
    @Serializable(with = OffsetDateTimeIso8601Serializer::class)
    val updatedAt: OffsetDateTime,
    val tagList: Set<String>,
)

@Serializable
data class SingleArticleResponse(val article: Article)

@Serializable
data class MultipleArticlesResponse(val articles: List<Article>, val articlesCount: Int)

@Serializable
data class CommentWrapper<T : Any>(val comment: T)

@Serializable
data class NewComment(val body: String) {
    context(_: Raise<IncorrectInput>)
    fun toCreateComment(slug: Slug, userId: UserId): CreateComment = withError({
        IncorrectInput(nonEmptyListOf(it))
    }) {
        CreateComment(userId, slug, Body(body))
    }
}

@Serializable
data class SingleCommentResponse(val comment: Comment)

@Serializable
data class Comment(
    val id: Long,
    @Serializable(with = OffsetDateTimeIso8601Serializer::class)
    val createdAt: OffsetDateTime,
    @Serializable(with = OffsetDateTimeIso8601Serializer::class)
    val updatedAt: OffsetDateTime,
    val body: String,
    val author: Profile,
)

@Serializable
data class MultipleCommentsResponse(val comments: List<Comment>)

@Serializable
data class NewArticle(
    val title: String,
    val description: String,
    val body: String,
    val tagList: List<String> = emptyList(),
) {
    context(_: Raise<IncorrectInput>)
    fun toCreateArticle(userId: UserId): CreateArticle = withError(::IncorrectInput) {
        accumulate {
            val title by accumulating { Title(title) }
            val description by accumulating { Description(description) }
            val body by accumulating { Body(body) }
            val tags by accumulating { tagList.validTags() }
            CreateArticle(userId, title, description, body, tags)
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
    fun toGetArticles(currentUserId: UserId?): GetArticles = withError(::IncorrectInput) {
        accumulate {
            val offset by accumulating { FeedOffset(offset) }
            val limit by accumulating { FeedLimit(limit) }
            val author by accumulating { author?.let { Username(it) } }
            val favorited by accumulating { favorited?.let { Username(it) } }
            GetArticles(limit, offset, author, favorited, tag, currentUserId)
        }
    }
}

class FeedParameters(data: ParameterStorage) : Parameters(data) {
    var offset: Int by parameter(default = 0)
    var limit: Int by parameter(default = 20)

    context(_: Raise<IncorrectInput>)
    fun toGetFeed(userId: UserId): GetFeed = withError(::IncorrectInput) {
        accumulate {
            val offset by accumulating { FeedOffset(offset) }
            val limit by accumulating { FeedLimit(limit) }
            GetFeed(userId, limit, offset)
        }
    }
}
