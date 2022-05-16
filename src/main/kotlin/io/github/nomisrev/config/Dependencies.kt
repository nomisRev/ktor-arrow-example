package io.github.nomisrev.config

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.repo.articlePersistence
import io.github.nomisrev.repo.userPersistence
import io.github.nomisrev.service.SlugGenerator
import io.github.nomisrev.service.slugifyGenerator

class Dependencies(
  val config: Config,
  val hikariDataSource: HikariDataSource,
  val userPersistence: UserPersistence,
  val articlePersistence: ArticlePersistence,
  val slugGenerator: SlugGenerator
)

fun dependencies(config: Config): Resource<Dependencies> = resource {
  val hikari = hikari(config.dataSource).bind()
  val sqlDelight = sqlDelight(hikari).bind()
  val userPersistence = userPersistence(sqlDelight.usersQueries)
  val articlePersistence = articlePersistence(sqlDelight.articlesQueries, sqlDelight.tagsQueries)
  val slugGenerator = slugifyGenerator()
  Dependencies(config, hikari, userPersistence, articlePersistence, slugGenerator)
}
