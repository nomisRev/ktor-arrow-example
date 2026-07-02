package io.github.nomisrev

import arrow.core.raise.either
import arrow.core.raise.context.bind
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.withError
import arrow.fx.coroutines.resourceScope
import io.github.nefilim.kjwt.DecodedJWT
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nefilim.kjwt.KJWTVerificationError
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.env.Dependencies
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.env.kotlinXSerializersModule
import io.github.nomisrev.users.RegisterUser
import io.github.nomisrev.users.UserId
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.extensions.testcontainers.toDataSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import org.testcontainers.containers.PostgreSQLContainer

private const val TEST_SCHEMA_PREFIX = "test_"

data class UserFixture(val username: String, val email: String, val password: String)

data class ArticleFixture(
    val title: String,
    val description: String,
    val body: String,
    val tags: Set<String>,
)

fun userFixture(password: String = "123456789"): UserFixture {
    val suffix = randomSuffix()
    val username = "user-$suffix"
    return UserFixture(username = username, email = "$username@domain.com", password = password)
}

fun articleFixture(): ArticleFixture {
    val suffix = randomSuffix()
    return ArticleFixture(
        title = "Article $suffix",
        description = "Description $suffix",
        body = "Body $suffix",
        tags = setOf("arrow-$suffix", "ktor-$suffix", "kotlin-$suffix", "sqldelight-$suffix"),
    )
}

fun randomSuffix(length: Int = 12): String = Uuid.random().toString().replace("-", "").take(length)

private fun randomSchemaName(): String = "$TEST_SCHEMA_PREFIX${randomSuffix(16)}"

suspend fun <A> withTestDependencies(block: suspend (Dependencies) -> A): A {
    val postgres = KotestProject.postgres()
    val schema = randomSchemaName()

    postgres.toDataSource().connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.execute("CREATE SCHEMA IF NOT EXISTS $schema")
        }
    }

    return resourceScope {
        val dependencies = dependencies(testEnv(postgres, schema))
        block(dependencies)
    }
}

suspend fun withServer(test: suspend HttpClient.(dep: Dependencies) -> Unit): Unit =
    withTestDependencies { dependencies ->
        testApplication {
            application { app(dependencies) }
            createClient {
                expectSuccess = false
                install(ContentNegotiation) {
                    json(Json { serializersModule = kotlinXSerializersModule })
                }
            }.use { client -> client.test(dependencies) }
        }
    }

private fun testEnv(postgres: PostgreSQLContainer<*>, schema: String): Env = Env().copy(
    dataSource = Env.DataSource(
        url = postgres.jdbcUrl.withCurrentSchema(schema),
        username = postgres.username,
        password = postgres.password,
        driver = postgres.driverClassName,
    ),
)

private fun String.withCurrentSchema(schema: String): String =
    if (contains("?")) "$this&currentSchema=$schema" else "$this?currentSchema=$schema"

data class RegisteredUser(val fixture: UserFixture, val token: JwtToken, val userId: UserId)

fun Dependencies.registerUser(fixture: UserFixture = userFixture()): RegisteredUser = either {
    val token = userService.register(RegisterUser(fixture.username, fixture.email, fixture.password))
    val jwt = withError({ JwtInvalid(it.toString()) }) {
        JWT.decodeT(token.value, JWSHMAC512Algorithm).bind<KJWTVerificationError, DecodedJWT<JWSHMAC512Algorithm>>()
    }
    val id = ensureNotNull(jwt.claimValueAsLong("id").getOrNull()) {
        JwtInvalid("id missing from JWT Token")
    }

    RegisteredUser(fixture, token, UserId(id))
}.shouldBeRight()
