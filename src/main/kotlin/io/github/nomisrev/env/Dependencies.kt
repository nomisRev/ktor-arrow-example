package io.github.nomisrev.env

import arrow.fx.coroutines.ResourceScope
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisrev.articles.ArticlePersistence
import io.github.nomisrev.articles.ArticleService
import io.github.nomisrev.articles.FavouriteService
import io.github.nomisrev.articles.SlugGenerator
import io.github.nomisrev.articles.slugifyGenerator
import io.github.nomisrev.auth.JwtConfig
import io.github.nomisrev.auth.JwtContext
import io.github.nomisrev.auth.JwtService
import io.github.nomisrev.tags.TagService
import io.github.nomisrev.users.UserService

class Dependencies(
    val userService: UserService,
    val jwtService: JwtConfig<JwtContext>,
    val articleService: ArticleService,
    val healthCheck: HealthCheckRegistry,
    val tagService: TagService,
)

suspend fun ResourceScope.dependencies(env: Env): Dependencies {
    val hikari = hikari(env.datasource)
    return dependencies(env, hikari)
}

suspend fun ResourceScope.dependencies(env: Env, hikari: HikariDataSource): Dependencies {
    val sqlDelight = sqlDelight(hikari)
    val articleRepo = ArticlePersistence(
        sqlDelight.articlesQueries,
        sqlDelight.commentsQueries,
        sqlDelight.tagsQueries,
    )
    val tagService = TagService(sqlDelight.tagsQueries)
    val favouriteService = FavouriteService(sqlDelight.favoritesQueries)

    val jwtService = JwtService(env.auth)
    val slugGenerator: SlugGenerator = slugifyGenerator()
    val userService = UserService(
        sqlDelight.usersQueries,
        sqlDelight.followingQueries,
        jwtService,
    )

    val checks = HealthCheckRegistry {
        register(HikariConnectionsHealthCheck(hikari, minConnections = 1))
    }

    return Dependencies(
        userService = userService,
        jwtService = jwtService.config,
        articleService = ArticleService(
            slugGenerator,
            articleRepo,
            userService,
            tagService,
            favouriteService,
        ),
        healthCheck = checks,
        tagService = tagService,
    )
}
