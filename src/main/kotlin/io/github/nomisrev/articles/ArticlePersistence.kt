package io.github.nomisrev.articles

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensureNotNull
import io.github.nomisrev.ArticleBySlugNotFound
import io.github.nomisrev.profiles.Profile
import io.github.nomisrev.sqldelight.*
import io.github.nomisrev.users.UserId
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
    @Suppress("LongParameterList")
    fun create(
        authorId: UserId,
        slug: Slug,
        title: String,
        description: String,
        body: String,
        createdAt: OffsetDateTime,
        updatedAt: OffsetDateTime,
        tags: Set<String>,
    ): ArticleId = articles.transactionWithResult {
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

    fun exists(slug: Slug): Boolean = articles.slugExists(slug.value).executeAsOne()

    fun feed(userId: UserId, limit: FeedLimit, offset: FeedOffset): List<Articles> =
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

    fun feedCount(userId: UserId): Long = articles.countFeedArticles(userId.serial).executeAsOne()

    fun allArticles(
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

        return ensureNotNull(article) { ArticleBySlugNotFound(slug.value) }
    }

    context(_: Raise<ArticleBySlugNotFound>)
    fun deleteArticle(slug: Slug) {
        val article = findArticleBySlug(slug)
        articles.delete(article.id)
    }

    fun createCommentForArticleSlug(
        userId: UserId,
        comment: String,
        articleId: ArticleId,
        createdAt: OffsetDateTime,
    ): Comments = comments.transactionWithResult {
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

    fun findCommentsForSlug(slug: Slug): List<Comment> =
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

    fun findCommentAuthor(commentId: Long): UserId? =
        comments.selectAuthorId(commentId).executeAsOneOrNull()?.let { UserId(it) }

    fun deleteComment(commentId: Long, authorId: UserId): Boolean =
        comments.delete(commentId, authorId.serial).executeAsList().size == 1
}
