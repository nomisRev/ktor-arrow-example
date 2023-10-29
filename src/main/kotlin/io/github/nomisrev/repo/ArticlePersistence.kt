package io.github.nomisrev.repo

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import io.github.nomisrev.ArticleBySlugNotFound
import io.github.nomisrev.service.Slug
import io.github.nomisrev.sqldelight.Articles
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

  suspend fun getArticleBySlug(slug: Slug): Either<ArticleBySlugNotFound, Articles>
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

    override suspend fun getArticleBySlug(slug: Slug): Either<ArticleBySlugNotFound, Articles> =
      either {
        val article = articles.selectBySlug(slug.value).executeAsOneOrNull()
        ensureNotNull(article) { ArticleBySlugNotFound(slug.value) }
      }
  }
