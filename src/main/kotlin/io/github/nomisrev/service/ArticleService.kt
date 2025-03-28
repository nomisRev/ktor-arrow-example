package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.nomisrev.DomainError
import io.github.nomisrev.NotArticleAuthor
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

interface ArticleService {
  /** Creates a new article and returns the resulting Article */
  suspend fun createArticle(input: CreateArticle): Either<DomainError, Article>

  /** Get the user's feed which contains articles of the authors the user followed */
  suspend fun getUserFeed(input: GetFeed): MultipleArticlesResponse

  /** Get article by Slug */
  suspend fun getArticleBySlug(slug: Slug): Either<DomainError, Article>

  /** Update an article and return the updated Article */
  suspend fun updateArticle(input: UpdateArticleInput): Either<DomainError, Article>

  /** Delete an article by slug */
  suspend fun deleteArticle(slug: Slug, userId: UserId): Either<DomainError, Unit>

  suspend fun insertCommentForArticleSlug(
    slug: Slug,
    userId: UserId,
    comment: String,
  ): Either<DomainError, Comments>

  suspend fun getCommentsForSlug(slug: Slug): List<Comment>
}

fun articleService(
  slugGenerator: SlugGenerator,
  articlePersistence: ArticlePersistence,
  userPersistence: UserPersistence,
  tagPersistence: TagPersistence,
  favouritePersistence: FavouritePersistence,
): ArticleService =
  object : ArticleService {
    override suspend fun deleteArticle(slug: Slug, userId: UserId): Either<DomainError, Unit> = either {
      val article = articlePersistence.getArticleBySlug(slug).bind()

      ensure(article.author_id == userId) {
        NotArticleAuthor(userId.serial, slug.value)
      }

      articlePersistence.deleteArticle(slug).bind()
    }

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

    override suspend fun updateArticle(input: UpdateArticleInput): Either<DomainError, Article> =
      either {
        val article = articlePersistence.getArticleBySlug(input.slug).bind()

        ensure(article.author_id != input.userId) {
          raise(NotArticleAuthor(input.userId.serial, input.slug.value))
        }

        val updatedArticle =
          articlePersistence
            .updateArticle(input.slug, input.title, input.description, input.body)
            .bind()

        val user = userPersistence.select(updatedArticle.author_id).bind()
        val articleTags = tagPersistence.selectTagsOfArticle(updatedArticle.id)
        val favouriteCount = favouritePersistence.favoriteCount(updatedArticle.id)

        Article(
          updatedArticle.id.serial,
          updatedArticle.slug,
          updatedArticle.title,
          updatedArticle.description,
          updatedArticle.body,
          Profile(user.username, user.bio, user.image, false),
          false,
          favouriteCount,
          updatedArticle.createdAt,
          updatedArticle.updatedAt,
          articleTags,
        )
      }

    override suspend fun getUserFeed(input: GetFeed): MultipleArticlesResponse {
      val articles =
        articlePersistence.getFeed(
          userId = input.userId,
          limit = FeedLimit(input.limit),
          offset = FeedOffset(input.offset),
        )

      return MultipleArticlesResponse(articles = articles, articlesCount = articles.size)
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
        articleTags,
      )
    }

    override suspend fun insertCommentForArticleSlug(slug: Slug, userId: UserId, comment: String) =
      either {
        val article = getArticleBySlug(slug).bind()
        articlePersistence.insertCommentForArticleSlug(
          slug,
          userId,
          comment,
          ArticleId(article.articleId),
          OffsetDateTime.now(),
        )
      }

    override suspend fun getCommentsForSlug(slug: Slug): List<Comment> =
      articlePersistence.getCommentsForSlug(slug).map { comment ->
        Comment(
          comment.comment__id,
          comment.comment__createdAt,
          comment.comment__updatedAt,
          comment.comment__body,
          Profile(comment.author__username, comment.author__bio, comment.author__image, false),
        )
      }
  }
