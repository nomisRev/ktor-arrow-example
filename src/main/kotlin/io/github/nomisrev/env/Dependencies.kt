package io.github.nomisrev.env

import arrow.fx.coroutines.continuations.ResourceScope
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.github.nomisrev.repo.articleRepo
import io.github.nomisrev.repo.userPersistence
import io.github.nomisrev.service.ArticleService
import io.github.nomisrev.service.JwtService
import io.github.nomisrev.service.UserService
import io.github.nomisrev.service.articleService
import io.github.nomisrev.service.jwtService
import io.github.nomisrev.service.slugifyGenerator
import io.github.nomisrev.service.userService
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers

class Dependencies(
  val userService: UserService,
  val jwtService: JwtService,
  val articleService: ArticleService,
  val healthCheck: HealthCheckRegistry
)

suspend fun ResourceScope.dependencies(env: Env): Dependencies {
  val hikari = hikari(env.dataSource)
  val sqlDelight = sqlDelight(hikari)
  val userRepo = userPersistence(sqlDelight.usersQueries)
  val articleRepo = articleRepo(sqlDelight.articlesQueries, sqlDelight.tagsQueries)
  val jwtService = jwtService(env.auth, userRepo)
  val slugGenerator = slugifyGenerator()
  val userService = userService(userRepo, jwtService)

  val checks = HealthCheckRegistry(Dispatchers.Default) {
    register(HikariConnectionsHealthCheck(hikari, 1), 5.seconds)
  }

  return Dependencies(
    userService,
    jwtService,
    articleService(slugGenerator, articleRepo, userRepo),
    checks
  )
}
