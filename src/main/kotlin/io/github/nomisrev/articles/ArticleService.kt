package io.github.nomisrev.articles

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.withError
import io.github.nomisrev.ArticleBySlugNotFound
import io.github.nomisrev.ArticleError
import io.github.nomisrev.CannotGenerateSlug
import io.github.nomisrev.CommentNotFound
import io.github.nomisrev.InvalidField
import io.github.nomisrev.NotArticleAuthor
import io.github.nomisrev.NotCommentAuthor
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.sqldelight.Articles
import io.github.nomisrev.sqldelight.Comments
import io.github.nomisrev.tags.TagService
import io.github.nomisrev.users.UserId
import io.github.nomisrev.users.UserPersistence
import io.github.nomisrev.users.Username

@JvmInline
value class ArticleId(val serial: Long)

data class FeedResult(val articles: List<Articles>, val articlesCount: Long)

@JvmInline
value class FeedOffset private constructor(val value: Long) {
    companion object {
        private const val MIN_FEED_OFFSET = 0

        context(_: Raise<InvalidField>)
        operator fun invoke(offset: Int): FeedOffset =
            withError({ InvalidField(it, "feed offset") }) {
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

        context(_: Raise<InvalidField>)
        operator fun invoke(limit: Int): FeedLimit = withError({ InvalidField(it, "feed limit") }) {
            ensure(limit >= MIN_FEED_LIMIT) { "too small, minimum is 1, and found $limit" }
            FeedLimit(limit.toLong())
        }
    }
}

@JvmInline
value class Title private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidField>)
        operator fun invoke(value: String): Title = Title(value.notBlankTrimmed("title"))
    }
}

@JvmInline
value class Description private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidField>)
        operator fun invoke(value: String): Description =
            Description(value.notBlankTrimmed("description"))
    }
}

@JvmInline
value class Body private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidField>)
        operator fun invoke(value: String): Body = Body(value.notBlankTrimmed("body"))
    }
}

context(_: Raise<InvalidField>)
private fun String.notBlankTrimmed(field: String): String = withError({ InvalidField(it, field) }) {
    ensure(isNotBlank()) { "Cannot be blank" }
    trim()
}

data class CreateArticle(
    val userId: UserId,
    val title: Title,
    val description: Description,
    val body: Body,
    val tags: Set<String>,
)

data class UpdateArticleInput(
    val slug: Slug,
    val userId: UserId,
    val title: String?,
    val description: String?,
    val body: String?,
)

data class GetFeed(val userId: UserId, val limit: FeedLimit, val offset: FeedOffset)

data class GetArticles(
    val limit: FeedLimit,
    val offset: FeedOffset,
    val author: Username? = null,
    val favorited: Username? = null,
    val tag: String? = null,
    val currentUserId: UserId? = null,
)

data class CreateComment(
    val userId: UserId,
    val slug: Slug,
    val body: Body,
)

class ArticleService(
    private val slugGenerator: SlugGenerator,
    private val articlePersistence: ArticlePersistence,
    private val userPersistence: UserPersistence,
    private val tagService: TagService,
    private val favouriteService: FavouriteService,
) {
    context(_: Raise<CannotGenerateSlug>, _: Raise<UserNotFound>)
    suspend fun createArticle(input: CreateArticle): Article {
        val slug = slugGenerator.generateSlug(input.title) { !articlePersistence.exists(it) }

        val insertAndGet = articlePersistence.create(
            input.userId,
            slug,
            input.title,
            input.description,
            input.body,
            input.tags,
        )

        val article = Articles(
            id = insertAndGet.id,
            slug = slug,
            title = input.title,
            description = input.description,
            body = input.body,
            author_id = input.userId,
            createdAt = insertAndGet.createdAt,
            updatedAt = insertAndGet.updatedAt,
        )

        return article(article, input.userId)
    }

    context(_: Raise<UserNotFound>)
    fun getUserFeed(input: GetFeed): MultipleArticlesResponse {
        val result = articlePersistence.feed(input)

        return MultipleArticlesResponse(
            articles = articles(result.articles, input.userId),
            articlesCount = result.articlesCount.toInt(),
        )
    }

    context(_: Raise<UserNotFound>)
    fun getAllArticles(input: GetArticles): MultipleArticlesResponse {
        val result = articlePersistence.allArticles(input)

        return MultipleArticlesResponse(
            articles = articles(result.articles, input.currentUserId),
            articlesCount = result.articlesCount.toInt(),
        )
    }

    context(_: Raise<UserNotFound>, _: Raise<ArticleBySlugNotFound>)
    fun getArticleBySlug(slug: Slug, currentUserId: UserId? = null): Article {
        val article = articlePersistence.findArticleBySlug(slug)
        return article(article, currentUserId)
    }

    context(_: Raise<UserNotFound>, _: Raise<ArticleError>)
    fun updateArticle(input: UpdateArticleInput): Article {
        val article = articlePersistence.findArticleBySlug(input.slug)

        ensure<ArticleError>(article.author_id == input.userId) {
            NotArticleAuthor(input.userId.serial, input.slug)
        }

        val updatedArticle = articlePersistence.updateArticle(
            input.slug,
            input.title,
            input.description,
            input.body,
        )

        return article(updatedArticle, input.userId)
    }

    context(_: Raise<ArticleError>)
    suspend fun deleteArticle(slug: Slug, userId: UserId) {
        val article = articlePersistence.findArticleBySlug(slug)
        ensure(article.author_id == userId) { NotArticleAuthor(userId.serial, slug) }
        articlePersistence.deleteArticle(slug)
    }

    context(_: Raise<UserNotFound>, _: Raise<ArticleBySlugNotFound>)
    fun insertComment(input: CreateComment): Comments {
        val article = getArticleBySlug(input.slug, input.userId)
        return articlePersistence.createCommentForArticleSlug(
            input.userId,
            input.body.value,
            ArticleId(article.articleId),
        )
    }

    fun getCommentsForSlug(slug: Slug): List<Comment> = articlePersistence.findCommentsForSlug(slug)

    context(_: Raise<ArticleError>)
    fun deleteComment(commentId: Long, userId: UserId) {
        val authorId = articlePersistence.findCommentAuthor(commentId)
        val authorIdNonNull = ensureNotNull(authorId) { CommentNotFound(commentId) }
        ensure(authorIdNonNull == userId) { NotCommentAuthor(userId.serial, commentId) }
        val _ = articlePersistence.deleteComment(commentId, userId)
    }

    context(_: Raise<UserNotFound>, _: Raise<ArticleBySlugNotFound>)
    suspend fun favoriteArticle(slug: Slug, userId: UserId): Article {
        val article = articlePersistence.findArticleBySlug(slug)
        val _ = favouriteService.favoriteArticle(userId, article.id)
        return article(article, userId)
    }

    context(_: Raise<UserNotFound>, _: Raise<ArticleBySlugNotFound>)
    suspend fun unfavoriteArticle(slug: Slug, userId: UserId): Article {
        val article = articlePersistence.findArticleBySlug(slug)
        val articleId = article.id
        favouriteService.unfavoriteArticle(userId, articleId)
        return article(article, userId)
    }

    context(_: Raise<UserNotFound>)
    private fun article(article: Articles, currentUserId: UserId?): Article =
        articles(listOf(article), currentUserId).single()

    context(_: Raise<UserNotFound>)
    private fun articles(articleRows: List<Articles>, currentUserId: UserId?): List<Article> {
        if (articleRows.isEmpty()) return emptyList()

        val articleIds = articleRows.map { it.id }
        val authorIds = articleRows.map { it.author_id }

        val profilesByAuthor = userPersistence.selectAuthorProfiles(currentUserId, authorIds)
        val tagsByArticle = tagService.selectTagsOfArticles(articleIds)
        val favoriteStatsByArticle = favouriteService.favoriteStats(currentUserId, articleIds)

        return articleRows.map { row ->
            val profile = ensureNotNull(profilesByAuthor[row.author_id]) {
                UserNotFound("userId=${row.author_id}")
            }

            val stats = favoriteStatsByArticle[row.id]
                ?: FavoriteStats(count = 0, favorited = false)

            Article(
                row.id.serial,
                row.slug,
                row.title.value,
                row.description.value,
                row.body.value,
                profile,
                stats.favorited,
                stats.count,
                row.createdAt,
                row.updatedAt,
                tagsByArticle[row.id].orEmpty().toSet(),
            )
        }
    }
}
