# Project Setup

This tutorial explains how the application boots: how configuration is loaded,
how `embeddedServer` is started with resource safety, and how the dependency
graph is assembled before the first request arrives.

---

## Configuration with `Env`

All configuration lives in a single data class hierarchy in `env/Env.kt`.
Each nested class reads from environment variables with sensible defaults for
local development:

```kotlin
data class Env(
    val dataSource: DataSource = DataSource(),
    val http: Http = Http(),
    val auth: Auth = Auth(),
) {
    data class Http(
        val host: String = getenv("HOST") ?: "0.0.0.0",
        val port: Int = getenv("SERVER_PORT")?.toIntOrNull() ?: 8080,
    )

    data class DataSource(
        val url: String = getenv("POSTGRES_URL") ?: "jdbc:postgresql://localhost:5432/ktor-arrow-example-database",
        val username: String = getenv("POSTGRES_USERNAME") ?: "postgres",
        val password: String = getenv("POSTGRES_PASSWORD") ?: "postgres",
        val driver: String = "org.postgresql.Driver",
    )

    data class Auth(
        val secret: String = getenv("JWT_SECRET") ?: "MySuperStrongSecret",
        val issuer: String = getenv("JWT_ISSUER") ?: "KtorArrowExampleIssuer",
        val duration: Duration = (getenv("JWT_DURATION")?.toIntOrNull() ?: 30).days,
    )
}
```

Key design decisions:

- **Plain data classes, no framework.** There is no config library, no HOCON
  parsing, and no injection container. `Env()` is a regular constructor call
  that reads `System.getenv` at the call site. This makes configuration
  trivially testable -- just pass different values to the constructor.
- **Defaults everywhere.** Running locally requires zero environment variables.
  The defaults match the `docker-compose.yaml` that ships with the project, so
  `docker-compose up -d` followed by `./gradlew run` works out of the box.
- **Grouped by concern.** `Http`, `DataSource`, and `Auth` each carry only the
  values their subsystem needs. When a function takes `Env.DataSource`, it
  cannot accidentally read the JWT secret.

---

## The entry point: `SuspendApp` and `embeddedServer`

The `main` function in `Main.kt` is the only entry point. It uses Arrow's
[SuspendApp](https://arrow-kt.io/ecosystem/suspendapp/) to get a
coroutine-aware `main` with proper JVM shutdown-hook handling:

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

This is four lines, but each one matters:

### 1. `SuspendApp { ... }`

`SuspendApp` replaces `fun main() = runBlocking { ... }` with a version that
installs JVM shutdown hooks. When the process receives `SIGTERM` or
`SIGINT`, the coroutine scope is cancelled gracefully instead of being
killed mid-flight. This matters in production: connections are closed, in-flight
requests complete, and resources are released in order.

### 2. `val env = Env()`

Configuration is loaded eagerly, before any resource is opened. If an
environment variable is missing or malformed, the process fails fast.

### 3. `resourceScope { ... }`

`resourceScope` is Arrow Fx's structured resource management. Every resource
acquired inside this block -- the HikariCP connection pool, the SqlDelight
driver, the Ktor server itself -- is guaranteed to be closed when the block
exits, in reverse acquisition order. This is the `try-with-resources` equivalent
for suspend functions, extended to handle an arbitrary number of resources
without nesting.

### 4. `server(Netty, ...) { app(dependencies) }`

This is SuspendApp's Ktor integration. It calls Ktor's `embeddedServer`
internally, but wraps it as a `Resource` so that the server is started and
stopped as part of the `resourceScope` lifecycle. The `Netty` argument selects
the engine. The trailing lambda is a standard Ktor `Application.() -> Unit`
configuration block.

### 5. `awaitCancellation()`

After the server starts, the coroutine suspends indefinitely. The process stays
alive until a shutdown signal arrives, at which point `SuspendApp` cancels the
scope, the `resourceScope` tears down all resources, and the process exits
cleanly.

---

## Ktor application configuration

The `app` function wires Ktor plugins and routes:

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
    install(Cohort) {
        verboseHealthCheckResponse = true
        healthcheck("/healthz/startup", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/liveness", HealthCheckRegistry(Dispatchers.Default))
        healthcheck("/healthz/readiness", module.healthCheck)
    }
}
```

`configure()` in `env/ktor.kt` installs the standard Ktor plugins:

- **`DefaultHeaders`** -- adds standard HTTP headers to every response.
- **`ContentNegotiation`** with `kotlinx.serialization` -- automatic JSON
  serialization and deserialization.
- **`CORS`** -- configured to allow `Authorization` and `Content-Type` headers
  from any origin.
- **`authentication`** with JWT -- verifies `Token <jwt>` headers using the
  HMAC512 verifier built by `JwtService`.

Each plugin is installed exactly once. Route handlers never configure
serialization or auth themselves -- they rely on the application-level setup.

---

## The dependency graph

`Dependencies` is a plain class that holds every service and persistence
instance the route layer needs:

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

The `dependencies()` factory function builds the graph inside `ResourceScope`:

```kotlin
suspend fun ResourceScope.dependencies(env: Env): Dependencies {
    val hikari = hikari(env.dataSource)
    val sqlDelight = sqlDelight(hikari)

    val userRepo = UserPersistence(sqlDelight.usersQueries, sqlDelight.followingQueries)
    val articleRepo = ArticlePersistence(
        sqlDelight.articlesQueries,
        sqlDelight.commentsQueries,
        sqlDelight.tagsQueries,
    )
    val tagPersistence = TagPersistence(sqlDelight.tagsQueries)
    val favouritePersistence = FavouritePersistence(sqlDelight.favoritesQueries)

    val jwtService = JwtService(env.auth, userRepo)
    val slugGenerator: SlugGenerator = slugifyGenerator()
    val userService = UserService(userRepo, jwtService)

    val checks = HealthCheckRegistry(Dispatchers.Default) {
        register(HikariConnectionsHealthCheck(hikari, 1), Duration.ZERO, 5.seconds)
    }

    return Dependencies(
        userService = userService,
        jwtService = jwtService.config,
        articleService = ArticleService(
            slugGenerator, articleRepo, userRepo, tagPersistence, favouritePersistence,
        ),
        healthCheck = checks,
        tagPersistence = tagPersistence,
        userPersistence = userRepo,
    )
}
```

The construction order matters:

1. **HikariCP connection pool** -- acquired as a `ResourceScope` resource via
   `autoCloseable`. If the database is unreachable, the process fails here
   before any server socket is opened.
2. **SqlDelight driver and schema** -- the JDBC driver is wrapped as a
   closeable resource, and `SqlDelight.Schema.create(driver)` runs the DDL
   migrations.
3. **Persistence layers** -- each one receives only the SqlDelight query
   objects it needs. `UserPersistence` gets `usersQueries` and
   `followingQueries`; `ArticlePersistence` gets `articlesQueries`,
   `commentsQueries`, and `tagsQueries`.
4. **Services** -- composed from persistence layers and cross-cutting
   concerns like `JwtService`.
5. **Health checks** -- Cohort's `HealthCheckRegistry` with a HikariCP
   connection check, exposed at `/healthz/readiness`. Empty registries
   serve `/healthz/startup` and `/healthz/liveness`.

There is no dependency injection framework. The graph is built by hand in a
single function, making the wiring explicit and easy to follow. Because the
function runs inside `ResourceScope`, any resource that implements
`AutoCloseable` is automatically closed on shutdown.

---

## Resource safety in detail

The `hikari()` and `sqlDelight()` helpers in `env/persistence.kt` show how
resources are registered:

```kotlin
suspend fun ResourceScope.hikari(env: Env.DataSource): HikariDataSource = autoCloseable {
    HikariDataSource(HikariConfig().apply {
        jdbcUrl = env.url
        username = env.username
        password = env.password
        driverClassName = env.driver
    })
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

`autoCloseable` and `closeable` register the resource with the enclosing
`ResourceScope`. When the scope is cancelled:

1. The Ktor server stops accepting new connections.
2. The SqlDelight JDBC driver is closed.
3. The HikariCP pool drains and closes.

This happens automatically, in reverse order, regardless of whether the
shutdown was triggered by a signal, an exception, or a test completing.

---

## Gradle dependencies

The project uses three version catalogs:

- `libs` -- declared in `gradle/libs.versions.toml` (Arrow, SqlDelight,
  Testcontainers, etc.)
- `ktorLibs` -- imported from `io.ktor:ktor-version-catalog` in
  `settings.gradle.kts`
- `arrow` -- imported from `io.arrow-kt:arrow-version-catalog` in
  `settings.gradle.kts`

The key runtime dependencies in `build.gradle.kts`:

| Dependency | Purpose |
|---|---|
| `libs.bundles.arrow` | Arrow Core, Arrow Fx Coroutines, SuspendApp, SuspendApp-Ktor |
| `ktorLibs.server.netty` | Ktor HTTP server with the Netty engine |
| `ktorLibs.server.contentNegotiation` | JSON request/response serialization |
| `ktorLibs.serialization.kotlinx.json` | kotlinx.serialization JSON format |
| `ktorLibs.server.auth.jwt` | Ktor JWT authentication plugin |
| `libs.kjwt.core` | JWT token generation (kJWT) |
| `libs.sqldelight.jdbc` | SqlDelight JDBC driver |
| `libs.hikari` | HikariCP connection pool |
| `libs.postgresql` | PostgreSQL JDBC driver |
| `libs.bundles.cohort` | Cohort health checks (Ktor + HikariCP) |
| `libs.slugify` | URL slug generation for article titles |

The Arrow bundle deserves a closer look. It pulls in four libraries:

```toml
[bundles]
arrow = [
    "arrow-core",      # Raise, Either, NonEmptyList, etc.
    "arrow-fx",        # ResourceScope, coroutine utilities
    "suspendapp",      # SuspendApp entry point with shutdown hooks
    "suspendapp-ktor", # server() helper that wraps embeddedServer as a Resource
]
```

`arrow-core` provides the `Raise` DSL used throughout the codebase for typed
error handling. `arrow-fx` provides `ResourceScope` for structured resource
management. `suspendapp` and `suspendapp-ktor` provide the `SuspendApp` entry
point and the `server()` bridge that connects Ktor's lifecycle to
`ResourceScope`.

---

## Startup sequence summary

```text
main()
  |
  +-- SuspendApp { ... }              Install JVM shutdown hooks
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

On shutdown, the sequence reverses: the server stops, the driver closes, the
pool drains, and the process exits.

---

## Where to go next

- [End-to-end feature](end-to-end-feature.md) -- trace a request from the HTTP
  contract through services and persistence
- [Validation](validation.md) -- how input validation accumulates errors
