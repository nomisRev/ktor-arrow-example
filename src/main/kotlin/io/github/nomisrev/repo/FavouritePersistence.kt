package io.github.nomisrev.repo

import io.github.nomisrev.sqldelight.FavoritesQueries

class FavouritePersistence(
    private val favouriteQueries: FavoritesQueries,
) {
    fun favoriteCount(articleId: ArticleId): Long =
        favouriteQueries.favoriteCount(articleId.serial).executeAsOne()

    fun isFavorite(userId: UserId, articleId: ArticleId): Boolean =
        favouriteQueries.isFavorite(userId.serial, articleId.serial).executeAsOneOrNull() != null

    suspend fun favoriteArticle(userId: UserId, articleId: ArticleId) =
        favouriteQueries.insert(articleId.serial, userId.serial).await()

    suspend fun unfavoriteArticle(userId: UserId, articleId: ArticleId) {
        favouriteQueries.delete(articleId.serial, userId.serial).await()
    }
}
