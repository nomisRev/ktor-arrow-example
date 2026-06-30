package io.github.nomisrev.repo

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import io.github.nomisrev.ArticleBySlugNotFound
import io.github.nomisrev.routes.Comment
import io.github.nomisrev.routes.FeedLimit
import io.github.nomisrev.routes.FeedOffset
import io.github.nomisrev.routes.Profile
import io.github.nomisrev.service.Slug
import io.github.nomisrev.sqldelight.*
import java.time.OffsetDateTime

@JvmInline value class ArticleId(val serial: Long)

data class ArticleListResult(
    val articles: List<Articles>,
    val articlesCount: Long,
)

class ArticlePersistence(
    private val articles: ArticlesQueries,
    private val comments: CommentsQueries,
    private val tagsQueries: TagsQueries,
) {
    /** Creates a new Article with the specified tags */
    @Suppress("LongParameterList")
    suspend fun create(
        authorId: UserId,
        slug: Slug,
        title: String,
        description: String,
        body: String,
        createdAt: OffsetDateTime,
        updatedAt: OffsetDateTime,
        tags: Set<String>,
    ): ArticleId =
        articles.transactionWithResult {
            val articleId =
                articles
                    .insertAndGetId(
                        slug.value,
                        title,
                        description,
                        body,
                        authorId,
                        createdAt,
                        updatedAt,
                    )
                    .executeAsOne()

            tags.forEach { tag -> tagsQueries.insert(articleId, tag) }

            articleId
        }

    /** Verifies if a certain slug already exists or not */
    suspend fun exists(slug: Slug): Boolean = articles.slugExists(slug.value).executeAsOne()

    /** Get recent articles from users you follow * */
    suspend fun feed(userId: UserId, limit: FeedLimit, offset: FeedOffset): List<Articles> =
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
                _,
                _,
                _,
                _ ->
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

    suspend fun feedCount(userId: UserId): Long =
        articles.countFeedArticles(userId.serial).executeAsOne()

    suspend fun allArticles(
        limit: FeedLimit,
        offset: FeedOffset,
        author: String? = null,
        favorited: String? = null,
        tag: String? = null,
    ): ArticleListResult {
        val query =
            when {
                !author.isNullOrBlank() ->
                    articles.selectArticlesByAuthor(
                        author,
                        limit.limit.toLong(),
                        offset.offset.toLong(),
                    )

                !favorited.isNullOrBlank() ->
                    articles.selectArticlesFavoritedByUsername(
                        favorited,
                        limit.limit.toLong(),
                        offset.offset.toLong(),
                    )

                !tag.isNullOrBlank() ->
                    articles.selectArticlesByTag(tag, limit.limit.toLong(), offset.offset.toLong())

                else -> articles.selectAllArticles(limit.limit.toLong(), offset.offset.toLong())
            }

        val count =
            when {
                !author.isNullOrBlank() -> articles.countArticlesByAuthor(author).executeAsOne()
                !favorited.isNullOrBlank() ->
                    articles.countArticlesFavoritedByUsername(favorited).executeAsOne()

                !tag.isNullOrBlank() -> articles.countArticlesByTag(tag).executeAsOne()
                else -> articles.countAllArticles().executeAsOne()
            }

        return ArticleListResult(query.executeAsList(), count)
    }

    suspend fun findArticleBySlug(slug: Slug): Either<ArticleBySlugNotFound, Articles> = either {
        val article = articles.selectBySlug(slug.value).executeAsOneOrNull()
        ensureNotNull(article) { ArticleBySlugNotFound(slug.value) }
    }

    suspend fun updateArticle(
        slug: Slug,
        title: String?,
        description: String?,
        body: String?,
    ): Either<ArticleBySlugNotFound, Articles> = either {
        val article =
            articles
                .update(title, description, body, OffsetDateTime.now(), slug.value) {
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
        ensureNotNull(article) { ArticleBySlugNotFound(slug.value) }
    }

    suspend fun deleteArticle(slug: Slug): Either<ArticleBySlugNotFound, Unit> = either {
        val article = findArticleBySlug(slug).bind()
        articles.delete(article.id)
    }

    suspend fun createCommentForArticleSlug(
        slug: Slug,
        userId: UserId,
        comment: String,
        articleId: ArticleId,
        createdAt: OffsetDateTime,
    ): Comments =
        comments.transactionWithResult {
            comments
                .insertAndGetComment(
                    article_id = articleId.serial,
                    body = comment,
                    author = userId.serial,
                    createdAt = createdAt,
                    updatedAt = createdAt,
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
        }

    suspend fun findCommentsForSlug(slug: Slug): List<Comment> =
        comments
            .selectForSlug(slug.value) {
                commentId,
                articleId,
                body,
                author,
                createdAt,
                updatedAt,
                username,
                bio,
                image ->
                Comment(
                    commentId,
                    createdAt,
                    updatedAt,
                    body,
                    Profile(username, bio, image, false),
                )
            }
            .executeAsList()

    suspend fun findCommentAuthor(commentId: Long): UserId? =
        comments.selectAuthorId(commentId).executeAsOneOrNull()?.let { UserId(it) }

    /** Delete a comment by ID */
    suspend fun deleteComment(commentId: Long, authorId: UserId): Boolean =
        comments.delete(commentId, authorId.serial).executeAsList().size == 1
}
