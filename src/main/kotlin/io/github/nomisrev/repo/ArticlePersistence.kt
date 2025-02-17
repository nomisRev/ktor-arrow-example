package io.github.nomisrev.repo

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import io.github.nomisrev.ArticleBySlugNotFound
import io.github.nomisrev.routes.Article
import io.github.nomisrev.routes.FeedLimit
import io.github.nomisrev.routes.FeedOffset
import io.github.nomisrev.routes.Profile
import io.github.nomisrev.service.Slug
import io.github.nomisrev.sqldelight.Articles
import io.github.nomisrev.sqldelight.ArticlesQueries
import io.github.nomisrev.sqldelight.Comments
import io.github.nomisrev.sqldelight.CommentsQueries
import io.github.nomisrev.sqldelight.TagsQueries
import java.time.OffsetDateTime

@JvmInline value class ArticleId(val serial: Long)

interface ArticlePersistence {
  @Suppress("LongParameterList")
  /** Creates a new Article with the specified tags */
  suspend fun create(
    authorId: UserId,
    slug: Slug,
    title: String,
    description: String,
    body: String,
    createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
    tags: Set<String>
  ): ArticleId

  /** Verifies if a certain slug already exists or not */
  suspend fun exists(slug: Slug): Boolean

  /** Get recent articles from users you follow * */
  suspend fun getFeed(userId: UserId, limit: FeedLimit, offset: FeedOffset): List<Article>

  suspend fun getArticleBySlug(slug: Slug): Either<ArticleBySlugNotFound, Articles>

  suspend fun insertCommentForArticleSlug(
    slug: Slug,
    userId: UserId,
    comment: String,
    articleId: ArticleId,
    createdAt: OffsetDateTime,
  ): Comments

  suspend fun getCommentsForSlug(slug: Slug): List<SelectForSlug>
}

fun articleRepo(articles: ArticlesQueries, comments: CommentsQueries, tagsQueries: TagsQueries) =
  object : ArticlePersistence {
    override suspend fun create(
      authorId: UserId,
      slug: Slug,
      title: String,
      description: String,
      body: String,
      createdAt: OffsetDateTime,
      updatedAt: OffsetDateTime,
      tags: Set<String>
    ): ArticleId =
      articles.transactionWithResult {
        val articleId =
          articles
            .insertAndGetId(slug.value, title, description, body, authorId, createdAt, updatedAt)
            .executeAsOne()

        tags.forEach { tag -> tagsQueries.insert(articleId, tag) }

        articleId
      }

    override suspend fun exists(slug: Slug): Boolean =
      articles.slugExists(slug.value).executeAsOne()

    override suspend fun getFeed(
      userId: UserId,
      limit: FeedLimit,
      offset: FeedOffset,
    ): List<Article> =
      articles
        .selectFeedArticles(
          userId.serial,
          limit.limit.toLong(),
          offset.offset.toLong(),
        ) {
          articleId,
          articleSlug,
          articleTitle,
          articleDescription,
          articleBody,
          articleAuthorId,
          articleCreatedAt,
          articleUpdatedAt,
          usersId,
          usersUsername,
          usersImage ->
          Article(
            articleId = articleId.serial,
            slug = articleSlug,
            title = articleTitle,
            description = articleDescription,
            body = articleBody,
            author = Profile(usersUsername, "", usersImage, true),
            favorited = false,
            favoritesCount = 0,
            createdAt = articleCreatedAt,
            updatedAt = articleUpdatedAt,
            tagList = listOf(),
          )
        }
        .executeAsList()

    override suspend fun getArticleBySlug(slug: Slug): Either<ArticleBySlugNotFound, Articles> =
      either {
        val article = articles.selectBySlug(slug.value).executeAsOneOrNull()
        ensureNotNull(article) { ArticleBySlugNotFound(slug.value) }
      }

    override suspend fun getCommentsForSlug(slug: Slug): List<SelectForSlug> =
      comments.selectForSlug(slug.value).executeAsList()

    override suspend fun insertCommentForArticleSlug(
      slug: Slug,
      userId: UserId,
      comment: String,
      articleId: ArticleId,
      createdAt: OffsetDateTime,
    ) =
      comments.transactionWithResult {
        comments
          .insertAndGetComment(
            article_id = articleId.serial,
            body = comment,
            author = userId.serial,
            createdAt = createdAt,
            updatedAt = createdAt
          ) { id, article_id, body, author, createdAt, updatedAt ->
            Comments(
              id = id,
              body = body,
              author = author,
              createdAt = createdAt,
              updatedAt = updatedAt,
              article_id = article_id
            )
          }
          .executeAsOne()
      }
  }
