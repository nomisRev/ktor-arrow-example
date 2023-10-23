package io.github.nomisrev.repo

import io.github.nomisrev.sqldelight.FavoritesQueries

interface FavouritePersistence {
  /** Get the favourite count */
  suspend fun favoriteCount(articleId: ArticleId): Long
}

fun favouritePersistence(favouriteQueries: FavoritesQueries) =
  object : FavouritePersistence {
    override suspend fun favoriteCount(articleId: ArticleId): Long =
      favouriteQueries.favoriteCount(articleId.serial).executeAsOne()
  }
