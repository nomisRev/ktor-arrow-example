package io.github.nomisrev.env

import arrow.fx.coroutines.continuations.ResourceScope
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.FavouritePersistence
import io.github.nomisrev.repo.TagPersistence
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.repo.articlePersistence
import io.github.nomisrev.repo.favouritePersistence
import io.github.nomisrev.repo.tagPersistence
import io.github.nomisrev.repo.userPersistence
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers

class Dependencies(
  val userPersistence: UserPersistence,
  val articlePersistence: ArticlePersistence,
  val healthCheck: HealthCheckRegistry,
  val tagPersistence: TagPersistence,
  val favouritePersistence: FavouritePersistence
)

suspend fun ResourceScope.dependencies(env: Env): Dependencies {
  val hikari = hikari(env.dataSource)
  val sqlDelight = sqlDelight(hikari)
  val userPersistence = userPersistence(sqlDelight.usersQueries, sqlDelight.followingQueries)
  val articlePersistence = articlePersistence(sqlDelight.articlesQueries, sqlDelight.tagsQueries)
  val tagPersistence = tagPersistence(sqlDelight.tagsQueries)
  val favouritePersistence = favouritePersistence(sqlDelight.favoritesQueries)

  val checks =
    HealthCheckRegistry(Dispatchers.Default) {
      register(HikariConnectionsHealthCheck(hikari, 1), 5.seconds)
    }

  return Dependencies(
    userPersistence,
    articlePersistence,
    checks,
    tagPersistence,
    favouritePersistence
  )
}
