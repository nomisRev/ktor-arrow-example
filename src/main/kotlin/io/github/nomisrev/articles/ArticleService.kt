package io.github.nomisrev.articles

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import io.github.nomisrev.ArticleError
import io.github.nomisrev.CommentNotFound
import io.github.nomisrev.DomainError
import io.github.nomisrev.NotArticleAuthor
import io.github.nomisrev.NotCommentAuthor
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.profiles.Profile
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
    context(_: Raise<DomainError>)
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

        val user = userPersistence.select(input.userId)

        return Article(
            insertAndGet.id.serial,
            slug.value,
            input.title,
            input.description,
            input.body,
            Profile(user.username, user.bio, user.image, false),
            false,
            0,
            insertAndGet.createdAt,
            insertAndGet.updatedAt,
            input.tags.toList(),
        )
    }

    context(_: Raise<UserNotFound>)
    fun getUserFeed(input: GetFeed): MultipleArticlesResponse {
        val articles =
            articlePersistence.feed(
                userId = input.userId,
                limit = FeedLimit(input.limit),
                offset = FeedOffset(input.offset),
            )

        val articlesCount = articlePersistence.feedCount(input.userId).toInt()

        return MultipleArticlesResponse(
            articles = articles.map { article(it, input.userId) },
            articlesCount = articlesCount,
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
            articles = result.articles.map { article(it, input.currentUserId) },
            articlesCount = result.articlesCount.toInt(),
        )
    }

    context(_: Raise<DomainError>)
    fun getArticleBySlug(slug: Slug, currentUserId: UserId? = null): Article {
        val article = articlePersistence.findArticleBySlug(slug)
        return article(article, currentUserId)
    }

    context(_: Raise<DomainError>)
    fun updateArticle(input: UpdateArticleInput): Article {
        val article = articlePersistence.findArticleBySlug(input.slug)

        ensure(article.author_id != input.userId) {
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
    fun deleteArticle(slug: Slug, userId: UserId) {
        val article = articlePersistence.findArticleBySlug(slug)
        ensure(article.author_id == userId) { NotArticleAuthor(userId.serial, slug.value) }
        articlePersistence.deleteArticle(slug)
    }

    context(_: Raise<DomainError>)
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
        articlePersistence.deleteComment(commentId, userId)
    }

    context(_: Raise<DomainError>)
    suspend fun favoriteArticle(slug: Slug, userId: UserId): Article {
        val article = articlePersistence.findArticleBySlug(slug)
        favouritePersistence.favoriteArticle(userId, article.id)
        return article(article, userId)
    }

    context(_: Raise<DomainError>)
    suspend fun unfavoriteArticle(slug: Slug, userId: UserId): Article {
        val article = articlePersistence.findArticleBySlug(slug)
        val articleId = article.id
        favouritePersistence.unfavoriteArticle(userId, articleId)
        return article(article, userId)
    }

    context(_: Raise<UserNotFound>)
    private fun article(article: Articles, currentUserId: UserId?): Article {
        val user = userPersistence.select(article.author_id)

        val articleTags = tagPersistence.selectTagsOfArticle(article.id)
        val favouriteCount = favouritePersistence.favoriteCount(article.id)
        val favorited =
            currentUserId != null && favouritePersistence.isFavorite(currentUserId, article.id)

        return Article(
            article.id.serial,
            article.slug,
            article.title,
            article.description,
            article.body,
            Profile(user.username, user.bio, user.image, false),
            favorited,
            favouriteCount,
            article.createdAt,
            article.updatedAt,
            articleTags,
        )
    }
}
