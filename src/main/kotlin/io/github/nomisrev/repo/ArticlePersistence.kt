package io.github.nomisrev.repo

import arrow.core.Either
import io.github.nomisrev.Unexpected
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
  ): Either<Unexpected, ArticleId>

  /** Verifies if a certain slug already exists or not */
  suspend fun exists(slug: Slug): Either<Unexpected, Boolean>
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
    ): Either<Unexpected, ArticleId> =
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

    override suspend fun exists(slug: Slug): Either<Unexpected, Boolean> =
      Either.catch { articles.slugExists(slug.value).executeAsOne() }.mapLeft { e ->
        Unexpected("Failed to check existence of $slug", e)
      }
  }
