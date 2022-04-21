package io.github.nomisrev.service

import arrow.core.continuations.EffectScope
import io.github.nomisrev.ApiError
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.routes.Article
import io.github.nomisrev.routes.Profile
import java.time.OffsetDateTime

data class CreateArticle(
  val userId: UserId,
  val title: String,
  val description: String,
  val body: String,
  val tags: Set<String>
)

interface ArticleService {
  /** Creates a new article and returns the resulting Article */
  context(EffectScope<ApiError>)
  suspend fun createArticle(input: CreateArticle): Article
}

fun articleService(
  slugGenerator: SlugGenerator,
  articlePersistence: ArticlePersistence,
  userPersistence: UserPersistence,
): ArticleService =
  object : ArticleService {
    context(EffectScope<ApiError>)
    override suspend fun createArticle(input: CreateArticle): Article {
      val slug =
        slugGenerator
          .generateSlug(input.title) { slug -> articlePersistence.exists(slug) }

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
            input.tags
          ).serial
      val user = userPersistence.select(input.userId)
      return Article(
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
        input.tags.toList()
      )
    }
  }
