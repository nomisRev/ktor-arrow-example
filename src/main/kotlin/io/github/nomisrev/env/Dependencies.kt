package io.github.nomisrev.env

import arrow.fx.coroutines.continuations.ResourceScope
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.github.nomisrev.repo.TagPersistence
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.repo.articleRepo
import io.github.nomisrev.repo.favouritePersistence
import io.github.nomisrev.repo.tagPersistence
import io.github.nomisrev.repo.userPersistence
import io.github.nomisrev.service.ArticleService
import io.github.nomisrev.service.JwtService
import io.github.nomisrev.service.ProfileService
import io.github.nomisrev.service.UserService
import io.github.nomisrev.service.articleService
import io.github.nomisrev.service.jwtService
import io.github.nomisrev.service.profileService
import io.github.nomisrev.service.slugifyGenerator
import io.github.nomisrev.service.userService
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.seconds

class Dependencies(
  val userService: UserService,
  val profileService: ProfileService,
  val jwtService: JwtService,
  val articleService: ArticleService,
  val healthCheck: HealthCheckRegistry,
  val tagPersistence: TagPersistence,
  val userPersistence: UserPersistence
)

suspend fun ResourceScope.dependencies(env: Env): Dependencies {
  val hikari = hikari(env.dataSource)
  val sqlDelight = sqlDelight(hikari)
  val userRepo = userPersistence(sqlDelight.usersQueries, sqlDelight.followingQueries)
  val articleRepo = articleRepo(sqlDelight.articlesQueries, sqlDelight.tagsQueries)
  val tagPersistence = tagPersistence(sqlDelight.tagsQueries)
  val favouritePersistence = favouritePersistence(sqlDelight.favoritesQueries)
  val jwtService = jwtService(env.auth, userRepo)
  val slugGenerator = slugifyGenerator()
  val userService = userService(userRepo, jwtService)
  val profileService = profileService(userRepo)

  val checks =
    HealthCheckRegistry(Dispatchers.Default) {
      register(HikariConnectionsHealthCheck(hikari, 1), 5.seconds)
    }

  return Dependencies(
    userService = userService,
    jwtService = jwtService,
    articleService = articleService(slugGenerator, articleRepo, userRepo, tagPersistence, favouritePersistence),
    healthCheck = checks,
    tagPersistence,
    userRepo
  )
}
