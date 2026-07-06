package io.github.nomisrev.articles

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensureNotNull
import io.github.nomisrev.ArticleBySlugNotFound
import io.github.nomisrev.profiles.Profile
import io.github.nomisrev.sqldelight.*
import io.github.nomisrev.users.UserId
import java.time.OffsetDateTime

@JvmInline value class ArticleId(val serial: Long)

data class FeedResult(
    val articles: List<Articles>,
    val articlesCount: Long,
)

class ArticlePersistence(
    private val articles: ArticlesQueries,
    private val comments: CommentsQueries,
    private val tagsQueries: TagsQueries,
) {
    @Suppress("LongParameterList")
    fun create(
        authorId: UserId,
        slug: Slug,
        title: String,
        description: String,
        body: String,
        tags: Set<String>,
    ): InsertAndReturn = articles.transactionWithResult {
        val insertAndReturn =
            articles
                .insertAndReturn(
                    slug.value,
                    title,
                    description,
                    body,
                    authorId,
                )
                .executeAsOne()

        tags.forEach { tag ->
            val _ = tagsQueries.insert(insertAndReturn.id, tag)
        }

        insertAndReturn
    }

    fun exists(slug: Slug): Boolean = articles.slugExists(slug.value).executeAsOne()

    fun feed(userId: UserId, limit: FeedLimit, offset: FeedOffset): FeedResult {
        var totalCount = 0L
        val rows =
            articles
                .selectFeedArticles(userId.serial, limit.limit.toLong(), offset.offset.toLong()) {
                    articleId,
                    articleSlug,
                    articleTitle,
                    articleDescription,
                    articleBody,
                    articleAuthorId,
                    articleCreatedAt,
                    articleUpdatedAt,
                    fullCount ->
                    totalCount = fullCount
                    Articles(
                        id = articleId,
                        slug = articleSlug,
                        title = articleTitle,
                        description = articleDescription,
                        body = articleBody,
                        author_id = articleAuthorId,
                        createdAt = articleCreatedAt,
                        updatedAt = articleUpdatedAt,
                    )
                }
                .executeAsList()
        return FeedResult(rows, totalCount)
    }

    /**
     * Returns the article page together with its total count in a single round trip per filter (via
     * `COUNT(*) OVER()`), instead of always issuing two separate, independently filtered queries
     * (one for the rows, one for `COUNT(*)`).
     */
    fun allArticles(
        limit: FeedLimit,
        offset: FeedOffset,
        author: String? = null,
        favorited: String? = null,
        tag: String? = null,
    ): FeedResult {
        var totalCount = 0L
        val mapper =
            {
                id: ArticleId,
                slug: String,
                title: String,
                description: String,
                body: String,
                authorId: UserId,
                createdAt: OffsetDateTime,
                updatedAt: OffsetDateTime,
                fullCount: Long,
                ->
                totalCount = fullCount
                Articles(id, slug, title, description, body, authorId, createdAt, updatedAt)
            }

        val rows =
            when {
                !author.isNullOrBlank() ->
                    articles.selectArticlesByAuthor(
                        author,
                        limit.limit.toLong(),
                        offset.offset.toLong(),
                        mapper,
                    )

                !favorited.isNullOrBlank() ->
                    articles.selectArticlesFavoritedByUsername(
                        favorited,
                        limit.limit.toLong(),
                        offset.offset.toLong(),
                        mapper,
                    )

                !tag.isNullOrBlank() ->
                    articles.selectArticlesByTag(
                        tag,
                        limit.limit.toLong(),
                        offset.offset.toLong(),
                        mapper,
                    )

                else ->
                    articles.selectAllArticles(limit.limit.toLong(), offset.offset.toLong(), mapper)
            }.executeAsList()

        return FeedResult(rows, totalCount)
    }

    context(_: Raise<ArticleBySlugNotFound>)
    fun findArticleBySlug(slug: Slug): Articles {
        val article = articles.selectBySlug(slug.value).executeAsOneOrNull()
        return ensureNotNull(article) { ArticleBySlugNotFound(slug.value) }
    }

    context(_: Raise<ArticleBySlugNotFound>)
    fun updateArticle(
        slug: Slug,
        title: String?,
        description: String?,
        body: String?,
    ): Articles {
        val article =
            articles
                .update(title, description, body, slug.value) {
                    articleId,
                    slug,
                    title,
                    description,
                    body,
                    authorId,
                    createdAt,
                    updatedAt ->
                    Articles(
                        id = articleId,
                        slug = slug,
                        title = title,
                        description = description,
                        body = body,
                        author_id = authorId,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    )
                }
                .executeAsOneOrNull()

        return ensureNotNull(article) { ArticleBySlugNotFound(slug.value) }
    }

    context(_: Raise<ArticleBySlugNotFound>)
    suspend fun deleteArticle(slug: Slug) {
        val article = findArticleBySlug(slug)
        articles.delete(article.id).await()
    }

    fun createCommentForArticleSlug(
        userId: UserId,
        comment: String,
        articleId: ArticleId,
    ): Comments =
        comments
            .insertAndGetComment(
                article_id = articleId.serial,
                body = comment,
                author = userId.serial,
            ) { id, articleId, body, author, createdAt, updatedAt ->
                Comments(
                    id = id,
                    body = body,
                    author = author,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    article_id = articleId,
                )
            }
            .executeAsOne()

    fun findCommentsForSlug(slug: Slug): List<Comment> =
        comments
            .selectForSlug(slug.value) { commentId, body, createdAt, updatedAt, username, bio, image
                ->
                Comment(
                    commentId,
                    createdAt,
                    updatedAt,
                    body,
                    Profile(username, bio, image, false),
                )
            }
            .executeAsList()

    fun findCommentAuthor(commentId: Long): UserId? =
        comments.selectAuthorId(commentId).executeAsOneOrNull()?.let { UserId(it) }

    fun deleteComment(commentId: Long, authorId: UserId): Boolean =
        comments.delete(commentId, authorId.serial).executeAsOneOrNull() != null
}
