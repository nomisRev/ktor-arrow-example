package io.github.nomisrev.articles

import io.github.nomisrev.sqldelight.FavoritesQueries
import io.github.nomisrev.users.UserId

private const val NO_USER = -1L

data class FavoriteStats(val count: Long, val favorited: Boolean)

class FavouritePersistence(
    private val favouriteQueries: FavoritesQueries,
) {
    fun favoriteStats(
        userId: UserId?,
        articleIds: Collection<ArticleId>,
    ): Map<ArticleId, FavoriteStats> =
        if (articleIds.isEmpty()) emptyMap()
        else
            favouriteQueries
                .selectFavoriteStatsForArticles(
                    userId?.serial ?: NO_USER,
                    articleIds.distinct().map { it.serial },
                ) { articleId, count, favorited ->
                    ArticleId(articleId) to FavoriteStats(count, favorited)
                }
                .executeAsList()
                .toMap()

    suspend fun favoriteArticle(userId: UserId, articleId: ArticleId) =
        favouriteQueries.insert(articleId.serial, userId.serial).await()

    suspend fun unfavoriteArticle(userId: UserId, articleId: ArticleId) {
        favouriteQueries.delete(articleId.serial, userId.serial).await()
    }
}
