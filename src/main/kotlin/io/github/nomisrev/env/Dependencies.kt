package io.github.nomisrev.env

import arrow.fx.coroutines.ResourceScope
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.FavouritePersistence
import io.github.nomisrev.repo.TagPersistence
import io.github.nomisrev.repo.UserPersistence
import io.github.nomisrev.service.ArticleService
import io.github.nomisrev.service.JwtService
import io.github.nomisrev.service.SlugGenerator
import io.github.nomisrev.service.UserService
import io.github.nomisrev.service.slugifyGenerator

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
        ArticlePersistence(sqlDelight.articlesQueries, sqlDelight.commentsQueries, sqlDelight.tagsQueries)
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
