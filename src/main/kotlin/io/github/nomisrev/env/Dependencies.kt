package io.github.nomisrev.env

import arrow.fx.coroutines.ResourceScope
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.github.nomisrev.articles.ArticlePersistence
import io.github.nomisrev.articles.ArticleService
import io.github.nomisrev.articles.FavouritePersistence
import io.github.nomisrev.articles.SlugGenerator
import io.github.nomisrev.articles.slugifyGenerator
import io.github.nomisrev.auth.JwtService
import io.github.nomisrev.tags.TagPersistence
import io.github.nomisrev.users.UserPersistence
import io.github.nomisrev.users.UserService

class Dependencies(
    val userService: UserService,
    val jwtService: JwtService,
    val articleService: ArticleService,
    val healthCheck: HealthCheckRegistry,
    val tagPersistence: TagPersistence,
    val userPersistence: UserPersistence,
)

suspend fun ResourceScope.dependencies(env: Env): Dependencies {
    val hikari = hikari(env.dataSource)
    val sqlDelight = sqlDelight(hikari)

    val userRepo = UserPersistence(sqlDelight.usersQueries, sqlDelight.followingQueries)
    val articleRepo =
        ArticlePersistence(
            sqlDelight.articlesQueries,
            sqlDelight.commentsQueries,
            sqlDelight.tagsQueries,
        )
    val tagPersistence = TagPersistence(sqlDelight.tagsQueries)
    val favouritePersistence = FavouritePersistence(sqlDelight.favoritesQueries)

    val jwtService = JwtService(env.auth, userRepo)
    val slugGenerator: SlugGenerator = slugifyGenerator()
    val userService = UserService(userRepo, jwtService)

    val checks = HealthCheckRegistry {
        register(HikariConnectionsHealthCheck(hikari, minConnections = 1))
    }

    return Dependencies(
        userService = userService,
        jwtService = jwtService,
        articleService =
            ArticleService(
                slugGenerator,
                articleRepo,
                userRepo,
                tagPersistence,
                favouritePersistence,
            ),
        healthCheck = checks,
        tagPersistence = tagPersistence,
        userPersistence = userRepo,
    )
}
