package io.github.nomisrev.repo

import arrow.core.Either
import arrow.core.continuations.EffectScope
import io.github.nomisrev.ApiError
import io.github.nomisrev.ApiError.Unexpected
import io.github.nomisrev.service.Slug
import io.github.nomisrev.sqldelight.ArticlesQueries
import io.github.nomisrev.sqldelight.TagsQueries
import java.time.OffsetDateTime

@JvmInline value class ArticleId(val serial: Long)

interface ArticlePersistence {
  /** Creates a new Article with the specified tags */
  context(EffectScope<ApiError>)
  @Suppress("LongParameterList")
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
  context(EffectScope<ApiError>)
  suspend fun exists(slug: Slug): Boolean
}

fun articleRepo(articles: ArticlesQueries, tagsQueries: TagsQueries) =
  object : ArticlePersistence {
    context(EffectScope<Unexpected>)
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
      Either.catch {
        articles.transactionWithResult<ArticleId> {
          val articleId =
            articles
              .insertAndGetId(slug.value, title, description, body, authorId, createdAt, updatedAt)
              .executeAsOne()

          tags.forEach { tag -> tagsQueries.insert(articleId, tag) }

          articleId
        }
      }
        .mapLeft { e -> Unexpected("Failed to create article: $authorId:$title:$tags", e) }
        .bind()

    context(EffectScope<Unexpected>)
    override suspend fun exists(slug: Slug): Boolean =
      Either.catch { articles.slugExists(slug.value).executeAsOne() }.mapLeft { e ->
        Unexpected("Failed to check existence of $slug", e)
      }.bind()
  }
