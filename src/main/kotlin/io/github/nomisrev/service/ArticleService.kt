package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.github.nomisrev.CommentNotFound
import io.github.nomisrev.DomainError
import io.github.nomisrev.NotArticleAuthor
import io.github.nomisrev.NotCommentAuthor
import io.github.nomisrev.repo.ArticleId
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.FavouritePersistence
import io.github.nomisrev.repo.TagPersistence
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.routes.Article
import io.github.nomisrev.routes.Comment
import io.github.nomisrev.routes.FeedLimit
import io.github.nomisrev.routes.FeedOffset
import io.github.nomisrev.routes.MultipleArticlesResponse
import io.github.nomisrev.routes.Profile
import io.github.nomisrev.sqldelight.Articles
import io.github.nomisrev.sqldelight.Comments
import java.time.OffsetDateTime

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
    /** Creates a new article and returns the resulting Article */
    suspend fun createArticle(input: CreateArticle): Either<DomainError, Article> =
        either {
            val slug =
                slugGenerator
                    .generateSlug(input.title) { slug -> articlePersistence.exists(slug).not() }
                    .bind()

            val createdAt = OffsetDateTime.now()
            val articleId =
                articlePersistence
                    .create(
                        input.userId,
                        slug,
                        input.title,
                        input.description,
                        input.body,
                        createdAt,
                        createdAt,
                        input.tags,
                    )
                    .serial

            val user = userPersistence.select(input.userId).bind()
            Article(
                articleId,
                slug.value,
                input.title,
                input.description,
                input.body,
                Profile(user.username, user.bio, user.image, false),
                false,
                0,
                createdAt,
                createdAt,
                input.tags.toList(),
            )
        }

    /** Get the user's feed which contains articles of the authors the user followed */
    suspend fun getUserFeed(input: GetFeed): MultipleArticlesResponse {
        val articles =
            articlePersistence.feed(
                userId = input.userId,
                limit = FeedLimit(input.limit),
                offset = FeedOffset(input.offset),
            )

        val articlesCount = articlePersistence.feedCount(input.userId).toInt()

        val responseArticles = mutableListOf<Article>()
        for (articleRow in articles) {
            responseArticles.add(article(articleRow, input.userId))
        }

        return MultipleArticlesResponse(
            articles = responseArticles,
            articlesCount = articlesCount,
        )
    }

    /** Get all articles */
    suspend fun getAllArticles(input: GetArticles): Either<DomainError, MultipleArticlesResponse> =
        either {
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

            val responseArticles = mutableListOf<Article>()
            for (articleRow in result.articles) {
                responseArticles.add(article(articleRow, input.currentUserId))
            }

            MultipleArticlesResponse(
                articles = responseArticles,
                articlesCount = result.articlesCount.toInt(),
            )
        }

    /** Get article by Slug */
    suspend fun getArticleBySlug(
        slug: Slug,
        currentUserId: UserId? = null,
    ): Either<DomainError, Article> =
        either {
            val article = articlePersistence.findArticleBySlug(slug).bind()
            article(article, currentUserId)
        }

    /** Update an article and return the updated Article */
    suspend fun updateArticle(input: UpdateArticleInput): Either<DomainError, Article> = either {
        val article = articlePersistence.findArticleBySlug(input.slug).bind()

        ensure(article.author_id != input.userId) {
            raise(NotArticleAuthor(input.userId.serial, input.slug.value))
        }

        val updatedArticle =
            articlePersistence
                .updateArticle(input.slug, input.title, input.description, input.body)
                .bind()

        article(updatedArticle, input.userId)
    }

    /** Delete an article by slug */
    suspend fun deleteArticle(slug: Slug, userId: UserId): Either<DomainError, Unit> =
        either {
            val article = articlePersistence.findArticleBySlug(slug).bind()
            ensure(article.author_id == userId) { NotArticleAuthor(userId.serial, slug.value) }
            articlePersistence.deleteArticle(slug).bind()
        }

    suspend fun insertCommentForArticleSlug(
        slug: Slug,
        userId: UserId,
        comment: String,
    ): Either<DomainError, Comments> = either {
        val article = getArticleBySlug(slug, userId).bind()
        articlePersistence.createCommentForArticleSlug(
            slug,
            userId,
            comment,
            ArticleId(article.articleId),
            OffsetDateTime.now(),
        )
    }

    suspend fun getCommentsForSlug(slug: Slug): List<Comment> =
        articlePersistence.findCommentsForSlug(slug)

    /** Delete a comment for an article */
    suspend fun deleteComment(
        slug: Slug,
        commentId: Long,
        userId: UserId,
    ): Either<DomainError, Unit> =
        either {
            val authorId = articlePersistence.findCommentAuthor(commentId)
            ensureNotNull(authorId) { CommentNotFound(commentId) }
            ensure(authorId == userId) { NotCommentAuthor(userId.serial, commentId) }
            articlePersistence.deleteComment(commentId, userId)
        }

    /** Favorite an article and return the updated article */
    suspend fun favoriteArticle(slug: Slug, userId: UserId): Either<DomainError, Article> =
        either {
            val article = articlePersistence.findArticleBySlug(slug).bind()
            val articleId = article.id

            favouritePersistence.favoriteArticle(userId, articleId)
            article(article, userId)
        }

    /** Unfavorite an article and return the updated article */
    suspend fun unfavoriteArticle(slug: Slug, userId: UserId): Either<DomainError, Article> =
        either {
            val article = articlePersistence.findArticleBySlug(slug).bind()
            val articleId = article.id
            favouritePersistence.unfavoriteArticle(userId, articleId)
            article(article, userId)
        }

    private suspend fun article(
        article: Articles,
        currentUserId: UserId?,
    ): Article {
        val user =
            when (val selectedUser = userPersistence.select(article.author_id)) {
                is Either.Left -> error("Author ${article.author_id.serial} not found")
                is Either.Right -> selectedUser.value
            }

        val articleTags = tagPersistence.selectTagsOfArticle(article.id)
        val favouriteCount = favouritePersistence.favoriteCount(article.id)
        val favorited =
            if (currentUserId != null) favouritePersistence.isFavorite(currentUserId, article.id) else false

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
