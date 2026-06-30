package io.github.nomisrev.repo

import io.github.nomisrev.sqldelight.FavoritesQueries

class FavouritePersistence(
    private val favouriteQueries: FavoritesQueries,
) {
    /** Get the favourite count of an article */
    suspend fun favoriteCount(articleId: ArticleId): Long =
        favouriteQueries.favoriteCount(articleId.serial).executeAsOne()

    /** Check if a user has favorited an article */
    suspend fun isFavorite(userId: UserId, articleId: ArticleId): Boolean =
        favouriteQueries.isFavorite(userId.serial, articleId.serial).executeAsOneOrNull() != null

    /** Favorite an article */
    suspend fun favoriteArticle(userId: UserId, articleId: ArticleId) {
        favouriteQueries.insert(articleId.serial, userId.serial)
    }

    /** Unfavorite an article */
    suspend fun unfavoriteArticle(userId: UserId, articleId: ArticleId) {
        favouriteQueries.delete(articleId.serial, userId.serial)
    }
}
