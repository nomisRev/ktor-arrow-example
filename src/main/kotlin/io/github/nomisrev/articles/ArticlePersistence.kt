package io.github.nomisrev.articles

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensureNotNull
import io.github.nomisrev.ArticleBySlugNotFound
import io.github.nomisrev.Body
import io.github.nomisrev.Description
import io.github.nomisrev.Title
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
        title: Title,
        description: Description,
        body: Body,
        tags: Set<String>,
    ): InsertAndReturn = articles.transactionWithResult {
        val insertAndReturn =
            articles
                .insertAndReturn(
                    slug,
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

    fun exists(slug: Slug): Boolean = articles.slugExists(slug).executeAsOne()

    fun feed(input: GetFeed): FeedResult {
        var totalCount = 0L
        val rows =
            articles
                .selectFeedArticles(
                    input.userId.serial,
                    input.limit.value,
                    input.offset.value,
                ) {
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
    fun allArticles(input: GetArticles): FeedResult {
        var totalCount = 0L
        val mapper =
            {
                id: ArticleId,
                slug: Slug,
                title: Title,
                description: Description,
                body: Body,
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
                !input.author.isNullOrBlank() ->
                    articles.selectArticlesByAuthor(
                        input.author,
                        input.limit.value,
                        input.offset.value,
                        mapper,
                    )

                !input.favorited.isNullOrBlank() ->
                    articles.selectArticlesFavoritedByUsername(
                        input.favorited,
                        input.limit.value,
                        input.offset.value,
                        mapper,
                    )

                !input.tag.isNullOrBlank() ->
                    articles.selectArticlesByTag(
                        input.tag,
                        input.limit.value,
                        input.offset.value,
                        mapper,
                    )

                else -> articles.selectAllArticles(input.limit.value, input.offset.value, mapper)
            }.executeAsList()

        return FeedResult(rows, totalCount)
    }

    context(_: Raise<ArticleBySlugNotFound>)
    fun findArticleBySlug(slug: Slug): Articles {
        val article = articles.selectBySlug(slug).executeAsOneOrNull()
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
                .update(title, description, body, slug) {
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
            .selectForSlug(slug) { commentId, body, createdAt, updatedAt, username, bio, image ->
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
