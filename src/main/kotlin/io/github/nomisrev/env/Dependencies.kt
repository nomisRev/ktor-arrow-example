package io.github.nomisrev.env

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import io.github.nomisrev.repo.articleRepo
import io.github.nomisrev.repo.userPersistence
import io.github.nomisrev.service.ArticleService
import io.github.nomisrev.service.DatabasePool
import io.github.nomisrev.service.JwtService
import io.github.nomisrev.service.UserService
import io.github.nomisrev.service.articleService
import io.github.nomisrev.service.databasePool
import io.github.nomisrev.service.jwtService
import io.github.nomisrev.service.slugifyGenerator
import io.github.nomisrev.service.userService

class Dependencies(
  val pool: DatabasePool,
  val userService: UserService,
  val jwtService: JwtService,
  val articleService: ArticleService
)

fun dependencies(env: Env): Resource<Dependencies> = resource {
  val hikari = hikari(env.dataSource).bind()
  val sqlDelight = sqlDelight(hikari).bind()
  val userRepo = userPersistence(sqlDelight.usersQueries)
  val articleRepo = articleRepo(sqlDelight.articlesQueries, sqlDelight.tagsQueries)
  val jwtService = jwtService(env.auth, userRepo)
  val slugGenerator = slugifyGenerator()
  val userService = userService(userRepo, jwtService)
  Dependencies(
    databasePool(hikari),
    userService,
    jwtService,
    articleService(slugGenerator, articleRepo, userRepo)
  )
}
