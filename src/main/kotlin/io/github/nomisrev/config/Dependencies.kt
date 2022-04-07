package io.github.nomisrev.config

import arrow.fx.coroutines.Resource
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

fun dependencies(config: Config): Resource<Dependencies> =
  hikari(config.dataSource).flatMap { hikari ->
    sqlDelight(hikari).map { sqlDelight ->
      val userRepo = userPersistence(sqlDelight.usersQueries)
      val articleRepo = articleRepo(sqlDelight.articlesQueries, sqlDelight.tagsQueries)
      val jwtService = jwtService(config.auth, userRepo)
      val slugGenerator = slugifyGenerator()
      val userService = userService(userRepo, jwtService)

      Dependencies(
        databasePool(hikari),
        userService,
        jwtService,
        articleService(slugGenerator, articleRepo, userRepo)
      )
    }
  }
