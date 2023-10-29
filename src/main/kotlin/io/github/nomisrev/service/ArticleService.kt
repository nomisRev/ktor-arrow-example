package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.raise.either
import io.github.nomisrev.DomainError
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.routes.*
import java.time.OffsetDateTime

data class CreateArticle(
  val userId: UserId,
  val title: String,
  val description: String,
  val body: String,
  val tags: Set<String>
)

data class GetFeed(
  val userId: UserId,
  val limit: Int,
  val offset: Int,
)

interface ArticleService {
  /** Creates a new article and returns the resulting Article */
  suspend fun createArticle(input: CreateArticle): Either<DomainError, Article>

  /** Get the user's feed which contains articles of the authors the user followed */
  suspend fun getUserFeed(input: GetFeed): MultipleArticlesResponse
}

fun articleService(
  slugGenerator: SlugGenerator,
  articlePersistence: ArticlePersistence,
  userPersistence: UserPersistence,
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

    override suspend fun getUserFeed(input: GetFeed): MultipleArticlesResponse {
      val articles =
        articlePersistence.getFeed(
          userId = input.userId,
          limit = FeedLimit(input.limit),
          offset = FeedOffset(input.offset)
        )

      return MultipleArticlesResponse(
        articles = articles,
        articlesCount = articles.size,
      )
    }
  }
