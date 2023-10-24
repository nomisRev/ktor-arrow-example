package io.github.nomisrev.repo

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.nomisrev.DomainError
import io.github.nomisrev.EmptyArticleList
import io.github.nomisrev.routes.Article
import io.github.nomisrev.routes.MultipleArticlesResponse
import io.github.nomisrev.routes.Profile
import io.github.nomisrev.service.Slug
import io.github.nomisrev.sqldelight.ArticlesQueries
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
  suspend fun getFeed(
    userId: UserId,
    limit: Long,
    offset: Long
  ): Either<DomainError, MultipleArticlesResponse>
}

fun articleRepo(articles: ArticlesQueries, tagsQueries: TagsQueries) =
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
      limit: Long,
      offset: Long,
    ): Either<DomainError, MultipleArticlesResponse> = either {
      val articleList =
        articles.transactionWithResult {
          val list =
            articles
              .selectFeedArticles(
                userId.serial,
                limit,
                offset,
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

          val count = list.size

          MultipleArticlesResponse(
            articles = list,
            articlesCount = count,
          )
        }
      //        ensure(articleList.articlesCount != 0) { EmptyArticleList("") }
      articleList
    }
  }
