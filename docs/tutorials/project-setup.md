# Project setup

Before the first request can be handled, we need to do a surprising amount of work:
read configuration, open a connection pool, create a SqlDelight driver, build the
service graph, start Ktor, and make sure everything shuts down in the right order.

This tutorial walks through that bootstrap path. The main idea is simple: use plain
Kotlin for wiring, and use Arrow's `ResourceScope` for resource safety.

---

## Configuration with `Env`

All configuration lives in `env/Env.kt`:

```kotlin
data class Env(
    val dataSource: DataSource = DataSource(),
    val http: Http = Http(),
    val auth: Auth = Auth(),
) {
    data class Http(
        val host: String = getenv("HOST") ?: "0.0.0.0",
        val port: Int = getenv("SERVER_PORT")?.toIntOrNull() ?: PORT,
    )

    data class DataSource(
        val url: String = getenv("POSTGRES_URL") ?: JDBC_URL,
        val username: String = getenv("POSTGRES_USERNAME") ?: JDBC_USER,
        val password: String = getenv("POSTGRES_PASSWORD") ?: JDBC_PW,
        val driver: String = JDBC_DRIVER,
    )

    data class Auth(
        val secret: String = getenv("JWT_SECRET") ?: AUTH_SECRET,
        val issuer: String = getenv("JWT_ISSUER") ?: AUTH_ISSUER,
        val duration: Duration = (getenv("JWT_DURATION")?.toIntOrNull() ?: AUTH_DURATION).days,
    )
}
```

There is no configuration framework here. `Env()` is a normal constructor call that
reads `System.getenv` and falls back to local defaults.

That gives us a couple of nice properties.

First, the defaults match the local PostgreSQL setup from `docker-compose.yaml`, so
running the project locally does not require a wall of environment variables.

Second, configuration is grouped by concern. A function that needs database settings can
take `Env.DataSource`; it cannot accidentally reach for the JWT secret.

Finally, this remains easy to test. We can construct `Env`, or one of its nested data
classes, directly.

---

## The entry point: `SuspendApp`

The whole application starts in `Main.kt`:

```kotlin
fun main() = SuspendApp {
    val env = Env()
    resourceScope {
        val dependencies = dependencies(env)
        val _ = server(Netty, host = env.http.host, port = env.http.port) { app(dependencies) }
        awaitCancellation()
    }
}
```

This is small, but it contains the entire lifecycle of the server.

`SuspendApp` gives us a coroutine-aware `main` with proper JVM shutdown-hook handling.
It is the production-friendly version of writing `runBlocking` ourselves. When the JVM
receives `SIGTERM` or `SIGINT`, the coroutine scope is cancelled and resources can be
released cleanly.

Then we build `Env()` before acquiring any resource. From that point on, we pass one
immutable configuration value through the rest of the bootstrap code.

The `resourceScope { ... }` block is where resource safety starts. Every resource
acquired inside this scope is released when the scope exits, in reverse acquisition
order. This is the `try-with-resources` idea, but for suspending code and without
nesting.

Finally, `server(Netty, ...) { app(dependencies) }` is SuspendApp's Ktor integration. It
wraps Ktor's `embeddedServer` as an Arrow resource, so starting and stopping the server
becomes part of the same lifecycle as the database resources.

`awaitCancellation()` keeps the process alive. Once shutdown begins, the coroutine is
cancelled, `resourceScope` unwinds, and the server, driver, and pool are closed.

---

## Ktor application configuration

`app` wires Ktor plugins and routes:

```kotlin
fun Application.app(module: Dependencies) {
    configure(module.jwtService)
    routing {
        userRoutes(module.userService, module.jwtService)
        tagRoutes(module.tagPersistence)
        articleRoutes(module.articleService, module.jwtService)
        commentRoutes(module.userService, module.articleService, module.jwtService)
        profileRoutes(module.userPersistence, module.jwtService)
    }
    install(Cohort) { healthcheck("/readiness", module.healthCheck) }
}
```

The `configure()` function in `env/ktor.kt` installs the shared Ktor plugins:

```kotlin
fun Application.configure(jwtConfig: JwtConfig<JwtContext>) {
    install(DefaultHeaders)
    install(ContentNegotiation) {
        json(
            Json {
                serializersModule = kotlinXSerializersModule
                isLenient = true
                ignoreUnknownKeys = true
            }
        )
    }
    install(CORS) {
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
        anyMethod()
        allowNonSimpleContentTypes = true
        maxAgeDuration = 3.days
    }
    authentication {
        jwt(jwtConfig.name) {
            authSchemes("Token")
            verifier(jwtConfig.verifier)
            validate(jwtConfig.validate)
        }
    }
}
```

So route handlers can stay focused on request handling. They do not configure JSON,
CORS, default headers, or JWT validation themselves.

The only health endpoint registered today is `/readiness`, backed by Cohort's
`HealthCheckRegistry`. It checks that Hikari can provide at least one database
connection.

---

## The dependency graph

`Dependencies` is a plain Kotlin class:

```kotlin
class Dependencies(
    val userService: UserService,
    val jwtService: JwtConfig<JwtContext>,
    val articleService: ArticleService,
    val healthCheck: HealthCheckRegistry,
    val tagPersistence: TagPersistence,
    val userPersistence: UserPersistence,
)
```

There is no dependency injection framework. The graph is built by hand in one function:

```kotlin
suspend fun ResourceScope.dependencies(env: Env): Dependencies {
    val hikari = hikari(env.dataSource)
    return dependencies(env, hikari)
}

suspend fun ResourceScope.dependencies(env: Env, hikari: HikariDataSource): Dependencies {
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
        jwtService = jwtService.config,
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
```

This is intentionally boring. And boring is good here.

We can read the construction order directly:

1. Acquire the HikariCP connection pool.
2. Create the SqlDelight JDBC driver and run `SqlDelight.Schema.create(driver)`.
3. Build persistence classes from the query objects they need.
4. Build services from persistence classes and cross-cutting concerns like `JwtService`.
5. Build the readiness health check.
6. Return one `Dependencies` object for the route layer.

Because the function runs in `ResourceScope`, resources that are registered with
`autoCloseable` or `closeable` are tied to the application lifecycle.

---

## Resource safety in detail

The resource helpers live in `env/persistence.kt`:

```kotlin
suspend fun ResourceScope.hikari(env: Env.DataSource): HikariDataSource = autoCloseable {
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = env.url
            username = env.username
            password = env.password
            driverClassName = env.driver
        }
    )
}

suspend fun ResourceScope.sqlDelight(dataSource: DataSource): SqlDelight {
    val driver = closeable { dataSource.asJdbcDriver() }
    SqlDelight.Schema.create(driver)
    return SqlDelight(
        driver,
        Articles.Adapter(articleIdAdapter, userIdAdapter),
        Tags.Adapter(articleIdAdapter),
        Users.Adapter(userIdAdapter),
    )
}
```

`autoCloseable` registers the `HikariDataSource` with the current `ResourceScope`.
`closeable` does the same for the SqlDelight JDBC driver.

When shutdown happens, the release order is the reverse of acquisition:

```text
server starts
  -> Hikari acquired
  -> SqlDelight driver acquired
  -> Ktor server acquired

shutdown
  -> Ktor server stops
  -> SqlDelight driver closes
  -> Hikari pool closes
```

That order is exactly what we want. The server stops accepting work before the database
resources disappear underneath it.

---

## Gradle dependencies

The project uses three version catalogs:

- `libs`, declared in `gradle/libs.versions.toml`.
- `ktorLibs`, imported from `io.ktor:ktor-version-catalog` in `settings.gradle.kts`.
- `arrow`, imported from `io.arrow-kt:arrow-version-catalog` in `settings.gradle.kts`.

The runtime dependencies in `build.gradle.kts` are grouped by concern:

| Dependency | Purpose |
|---|---|
| `libs.bundles.arrow` | Arrow Core, Arrow Fx Coroutines, SuspendApp, SuspendApp-Ktor |
| `ktorLibs.server.netty` | Ktor HTTP server with the Netty engine |
| `ktorLibs.server.defaultHeaders` | Standard response headers |
| `ktorLibs.server.cors` | CORS configuration |
| `ktorLibs.server.contentNegotiation` | JSON request/response serialization |
| `ktorLibs.serialization.kotlinx.json` | kotlinx.serialization JSON format |
| `ktorLibs.server.auth.jwt` | Ktor JWT authentication plugin |
| `libs.spine.api`, `libs.spine.server`, `libs.spine.server.arrow` | Typed endpoint contracts and route integration |
| `libs.kjwt.core` | JWT token generation |
| `libs.sqldelight.jdbc` | SqlDelight JDBC driver |
| `libs.hikari` | HikariCP connection pool |
| `libs.postgresql` | PostgreSQL JDBC driver |
| `libs.bundles.cohort` | Cohort readiness checks |
| `libs.slugify` | URL slug generation for article titles |
| `libs.logback.classic` | Logging backend |

The Arrow bundle is small but important:

```toml
[bundles]
arrow = [
    "arrow-core",
    "arrow-fx",
    "suspendapp",
    "suspendapp-ktor",
]
```

`arrow-core` gives us `Raise`, `NonEmptyList`, `recover`, and the typed-error DSL.
`arrow-fx` gives us `ResourceScope`. `suspendapp` and `suspendapp-ktor` connect the
application entry point and Ktor server lifecycle to those resources.

---

## Startup sequence summary

Putting it all together, startup looks like this:

```text
main()
  |
  +-- SuspendApp { ... }              Install shutdown handling
  |
  +-- Env()                           Read environment variables
  |
  +-- resourceScope { ... }           Open structured resource scope
       |
       +-- hikari(env.dataSource)     Create HikariCP connection pool
       |
       +-- sqlDelight(hikari)         Create JDBC driver, run DDL
       |
       +-- dependencies(env)          Wire persistence -> services -> Dependencies
       |
       +-- server(Netty, ...) {       Start Ktor with Netty
       |       app(dependencies)        Install plugins, mount routes
       |   }
       |
       +-- awaitCancellation()        Suspend until shutdown signal
```

And on shutdown the order reverses: Ktor stops, the SqlDelight driver closes, Hikari
closes, and the process exits.

No container is hiding that from us. The wiring is plain Kotlin, while resource safety is
handled by `ResourceScope`.

---

## Where to go next

- [End-to-end feature](end-to-end-feature.md): trace registration from HTTP to the
  database and back.
- [Validation](validation.md): see how request validation accumulates all field errors.

Thank you for reading! Next we can look at how this setup supports typed errors across a
full feature without making the route handlers noisy.
