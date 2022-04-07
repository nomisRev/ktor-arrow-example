package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.computations.either
import io.github.nomisrev.ApiError
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.routes.Article
import io.github.nomisrev.routes.Profile
import java.time.OffsetDateTime

interface ArticleService {
  suspend fun createArticle(
    userId: UserId,
    title: String,
    description: String,
    body: String,
    tags: Set<String>
  ): Either<ApiError, Article>
}

// TODO clean the hell out of this impl
// Replace all the SqlDelight Queries by Repos
@Suppress("LongParameterList")
fun articleService(
  slugGenerator: SlugGenerator,
  articlePersistence: ArticlePersistence,
  userPersistence: UserPersistence,
): ArticleService =
  object : ArticleService {
    override suspend fun createArticle(
      userId: UserId,
      title: String,
      description: String,
      body: String,
      tags: Set<String>
    ): Either<ApiError, Article> = either {
      val slug =
        slugGenerator.generateSlug(title) { slug -> articlePersistence.exists(slug).bind() }.bind()
      val createdAt = OffsetDateTime.now()
      val articleId =
        articlePersistence
          .create(userId, slug, title, description, body, createdAt, createdAt, tags)
          .bind()
          .serial
      val user = userPersistence.select(userId).bind()
      Article(
        articleId,
        slug.value,
        title,
        description,
        body,
        Profile(user.username, user.bio, user.image, false),
        false,
        0,
        createdAt,
        createdAt,
        tags.toList()
      )
    }
  }
