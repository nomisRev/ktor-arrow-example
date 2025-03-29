package io.github.nomisrev.repo

import io.github.nomisrev.sqldelight.FavoritesQueries

interface FavouritePersistence {
  /** Get the favourite count of an article */
  suspend fun favoriteCount(articleId: ArticleId): Long

  /** Check if a user has favorited an article */
  suspend fun isFavorite(userId: UserId, articleId: ArticleId): Boolean

  /** Favorite an article */
  suspend fun favoriteArticle(userId: UserId, articleId: ArticleId)

  /** Unfavorite an article */
  suspend fun unfavoriteArticle(userId: UserId, articleId: ArticleId)
}

fun favouritePersistence(favouriteQueries: FavoritesQueries) =
  object : FavouritePersistence {
    override suspend fun favoriteCount(articleId: ArticleId): Long =
      favouriteQueries.favoriteCount(articleId.serial).executeAsOne()

    override suspend fun isFavorite(userId: UserId, articleId: ArticleId): Boolean =
      favouriteQueries.isFavorite(userId.serial, articleId.serial).executeAsOneOrNull() != null

    override suspend fun favoriteArticle(userId: UserId, articleId: ArticleId) {
      favouriteQueries.insert(articleId.serial, userId.serial)
    }

    override suspend fun unfavoriteArticle(userId: UserId, articleId: ArticleId) {
      favouriteQueries.delete(articleId.serial, userId.serial)
    }
  }
