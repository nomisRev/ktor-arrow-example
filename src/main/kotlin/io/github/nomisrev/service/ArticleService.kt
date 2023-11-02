@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.service

import arrow.core.raise.Raise
import io.github.nomisrev.DomainError
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.FavouritePersistence
import io.github.nomisrev.repo.TagPersistence
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.routes.Article
import io.github.nomisrev.routes.FeedLimit
import io.github.nomisrev.routes.FeedOffset
import io.github.nomisrev.routes.MultipleArticlesResponse
import io.github.nomisrev.routes.Profile
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

/** Creates a new article and returns the resulting Article */
context(Raise<DomainError>, SlugGenerator, ArticlePersistence, UserPersistence)
suspend fun createArticle(input: CreateArticle): Article {
  val slug = generateSlug(input.title) { slug -> !exists(slug) }
  val createdAt = OffsetDateTime.now()
  val articleId = create(
    input.userId,
    slug,
    input.title,
    input.description,
    input.body,
    createdAt,
    createdAt,
    input.tags
  ).serial
  val user = select(input.userId)
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

context(ArticlePersistence)
suspend fun getUserFeed(input: GetFeed): MultipleArticlesResponse {
  val articles =
    selectFeed(
      userId = input.userId,
      limit = FeedLimit(input.limit),
      offset = FeedOffset(input.offset)
    )

  return MultipleArticlesResponse(
    articles = articles,
    articlesCount = articles.size,
  )
}

context(
  Raise<DomainError>,
  SlugGenerator,
  ArticlePersistence,
  TagPersistence,
  FavouritePersistence,
  UserPersistence
)
suspend fun articleBySlug(slug: Slug): Article {
  val article = selectArticleBySlug(slug).bind()
  val user = select(article.author_id)
  val articleTags = selectTagsOfArticle(article.id)
  val favouriteCount = favoriteCount(article.id)
  return Article(
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
