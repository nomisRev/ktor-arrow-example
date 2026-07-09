package io.github.nomisrev.articles

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import io.github.nomisrev.ArticleError
import io.github.nomisrev.CommentNotFound
import io.github.nomisrev.DomainErrors
import io.github.nomisrev.NotArticleAuthor
import io.github.nomisrev.NotCommentAuthor
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.sqldelight.Articles
import io.github.nomisrev.sqldelight.Comments
import io.github.nomisrev.tags.TagPersistence
import io.github.nomisrev.users.UserId
import io.github.nomisrev.users.UserPersistence

data class CreateArticle(
    val userId: UserId,
    val title: String,
    val description: String,
    val body: String,
    val tags: Set<String>,
)

data class UpdateArticleInput(
    val slug: Slug,
    val userId: UserId,
    val title: String?,
    val description: String?,
    val body: String?,
)

data class GetFeed(val userId: UserId, val limit: Int, val offset: Int)

data class GetArticles(
    val limit: Int,
    val offset: Int,
    val author: String? = null,
    val favorited: String? = null,
    val tag: String? = null,
    val currentUserId: UserId? = null,
)

class ArticleService(
    private val slugGenerator: SlugGenerator,
    private val articlePersistence: ArticlePersistence,
    private val userPersistence: UserPersistence,
    private val tagPersistence: TagPersistence,
    private val favouritePersistence: FavouritePersistence,
) {
    context(_: DomainErrors)
    suspend fun createArticle(input: CreateArticle): Article {
        val slug =
            slugGenerator.generateSlug(input.title) { slug ->
                articlePersistence.exists(slug).not()
            }

        val insertAndGet =
            articlePersistence.create(
                input.userId,
                slug,
                input.title,
                input.description,
                input.body,
                input.tags,
            )

        val article =
            Articles(
                id = insertAndGet.id,
                slug = slug.value,
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
        val result =
            articlePersistence.feed(
                userId = input.userId,
                limit = FeedLimit(input.limit),
                offset = FeedOffset(input.offset),
            )

        return MultipleArticlesResponse(
            articles = articles(result.articles, input.userId),
            articlesCount = result.articlesCount.toInt(),
        )
    }

    context(_: Raise<UserNotFound>)
    fun getAllArticles(input: GetArticles): MultipleArticlesResponse {
        val limit = FeedLimit(input.limit)
        val offset = FeedOffset(input.offset)

        val result =
            articlePersistence.allArticles(
                limit = limit,
                offset = offset,
                author = input.author,
                favorited = input.favorited,
                tag = input.tag,
            )

        return MultipleArticlesResponse(
            articles = articles(result.articles, input.currentUserId),
            articlesCount = result.articlesCount.toInt(),
        )
    }

    context(_: DomainErrors)
    fun getArticleBySlug(slug: Slug, currentUserId: UserId? = null): Article {
        val article = articlePersistence.findArticleBySlug(slug)
        return article(article, currentUserId)
    }

    context(_: DomainErrors)
    fun updateArticle(input: UpdateArticleInput): Article {
        val article = articlePersistence.findArticleBySlug(input.slug)

        ensure(article.author_id == input.userId) {
            NotArticleAuthor(input.userId.serial, input.slug.value)
        }

        val updatedArticle =
            articlePersistence.updateArticle(
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
        ensure(article.author_id == userId) { NotArticleAuthor(userId.serial, slug.value) }
        articlePersistence.deleteArticle(slug)
    }

    context(_: DomainErrors)
    fun insertCommentForArticleSlug(slug: Slug, userId: UserId, comment: String): Comments {
        val article = getArticleBySlug(slug, userId)
        return articlePersistence.createCommentForArticleSlug(
            userId,
            comment,
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

    context(_: DomainErrors)
    suspend fun favoriteArticle(slug: Slug, userId: UserId): Article {
        val article = articlePersistence.findArticleBySlug(slug)
        val _ = favouritePersistence.favoriteArticle(userId, article.id)
        return article(article, userId)
    }

    context(_: DomainErrors)
    suspend fun unfavoriteArticle(slug: Slug, userId: UserId): Article {
        val article = articlePersistence.findArticleBySlug(slug)
        val articleId = article.id
        favouritePersistence.unfavoriteArticle(userId, articleId)
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
        val tagsByArticle = tagPersistence.selectTagsOfArticles(articleIds)
        val favoriteStatsByArticle = favouritePersistence.favoriteStats(currentUserId, articleIds)

        return articleRows.map { row ->
            val profile =
                ensureNotNull(profilesByAuthor[row.author_id]) {
                    UserNotFound("userId=${row.author_id}")
                }
            val stats =
                favoriteStatsByArticle[row.id] ?: FavoriteStats(count = 0, favorited = false)

            Article(
                row.id.serial,
                row.slug,
                row.title,
                row.description,
                row.body,
                profile,
                stats.favorited,
                stats.count,
                row.createdAt,
                row.updatedAt,
                tagsByArticle[row.id].orEmpty(),
            )
        }
    }
}
