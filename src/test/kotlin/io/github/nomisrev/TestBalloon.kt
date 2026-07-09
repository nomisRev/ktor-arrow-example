package io.github.nomisrev

import arrow.core.raise.context.Raise
import arrow.core.raise.context.bind
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.withError
import arrow.core.raise.recover
import arrow.fx.coroutines.resourceScope
import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestElementName
import de.infix.testBalloon.framework.shared.TestRegistering
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.articles.Article
import io.github.nomisrev.articles.ArticleService
import io.github.nomisrev.articles.CreateArticle
import io.github.nomisrev.env.Dependencies
import io.github.nomisrev.env.Env
import io.github.nomisrev.env.dependencies
import io.github.nomisrev.env.kotlinXSerializersModule
import io.github.nomisrev.users.RegisterUser
import io.github.nomisrev.users.UserId
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

@TestRegistering
fun <E> TestSuite.testRaise(
    @TestElementName name: String,
    testConfig: TestConfig = TestConfig,
    test: suspend context(Raise<E>) Test.ExecutionScope.() -> Unit,
) = test(name, testConfig) {
    recover({
        test()
    }) { error: E ->
        throw AssertionError("Expected no errors but found $error")
    }
}

@TestRegistering
fun TestSuite.testDependencies(
    @TestElementName name: String,
    testConfig: TestConfig = TestConfig,
    test: suspend context(Dependencies, DomainErrors) Test.ExecutionScope.() -> Unit,
) = test(name, testConfig) {
    resourceScope {
        val dependencies = dependencies(Env(), PostgreSQL.dataSource)
        recover({
            test(
                dependencies,
                contextOf<DomainErrors>(),
                this@test,
            )
        }) { error: DomainError ->
            throw AssertionError("Expected no errors but found $error")
        }
    }
}

@TestRegistering
fun TestSuite.testServer(
    @TestElementName name: String,
    testConfig: TestConfig = TestConfig,
    test: suspend context(Dependencies, HttpClient, DomainErrors) Test.ExecutionScope.() -> Unit,
) = test(name, testConfig) {
    resourceScope {
        val dependencies = dependencies(Env(), PostgreSQL.dataSource)
        testApplication {
            application { app(dependencies) }
            createClient {
                expectSuccess = false
                install(ContentNegotiation) {
                    json(Json { serializersModule = kotlinXSerializersModule })
                }
            }.use { client ->
                recover({
                    test(
                        dependencies,
                        client,
                        contextOf<DomainErrors>(),
                        this@test,
                    )
                }) { error: DomainError ->
                    throw AssertionError("Expected no errors but found $error")
                }
            }
        }
    }
}

context(client: HttpClient)
val client: HttpClient
    get() = client

context(dependencies: Dependencies)
val dependencies: Dependencies
    get() = dependencies

context(_: DomainErrors)
suspend fun ArticleService.createArticle(userId: UserId, article: ArticleFixture = articleFixture()): Article =
    createArticle(
        CreateArticle(
            userId,
            article.title,
            article.description,
            article.body,
            article.tags,
        )
    )

context(dependencies: Dependencies, _: DomainErrors)
fun registerUser(fixture: UserFixture = userFixture()): RegisteredUser {
    val token = dependencies.userService.register(RegisterUser(fixture.username, fixture.email, fixture.password))
    val jwt = withError({ JwtInvalid(it.toString()) }) {
        JWT.decodeT(token.value, JWSHMAC512Algorithm).bind()
    }
    val id = ensureNotNull(jwt.claimValueAsLong("id").getOrNull()) {
        JwtInvalid("id missing from JWT Token")
    }

    return RegisteredUser(fixture, token, UserId(id))
}
