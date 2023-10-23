package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.raise.either
import io.github.nomisrev.DomainError
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.FavouritePersistence
import io.github.nomisrev.repo.TagPersistence
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
  suspend fun createArticle(input: CreateArticle): Either<DomainError, Article>

  /** Get article by Slug */
  suspend fun getArticleBySlug(slug: Slug): Either<DomainError, Article>
}

fun articleService(
  slugGenerator: SlugGenerator,
  articlePersistence: ArticlePersistence,
  userPersistence: UserPersistence,
  tagPersistence: TagPersistence,
  favouritePersistence: FavouritePersistence
): ArticleService =
  object : ArticleService {
    override suspend fun createArticle(input: CreateArticle): Either<DomainError, Article> =
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
              input.tags
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
          input.tags.toList()
        )
      }

    override suspend fun getArticleBySlug(slug: Slug): Either<DomainError, Article> = either {
      val article = articlePersistence.getArticleBySlug(slug).bind()
      val user = userPersistence.select(article.author_id).bind()
      val articleTags = tagPersistence.selectTagsOfArticle(article.id)
      val favouriteCount = favouritePersistence.favoriteCount(article.id)
      Article(
        article.id.serial,
        slug.value,
        article.title,
        article.description,
        article.body,
        Profile(user.username, user.bio, user.image, false),
        false,
        favouriteCount,
        article.createdAt,
        article.createdAt,
        articleTags
      )
    }
  }
