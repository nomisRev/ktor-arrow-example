# Service Architecture

This is a single Ktor service (the RealWorld "Conduit" API) wired manually with Arrow's `ResourceScope`, not a
multi-service/multi-module platform. Follow the shapes below when touching bootstrap, configuration, or dependency
wiring.

## Module structure

Packages are feature-first under `io.github.nomisrev`:

```
io.github.nomisrev/
├── Main.kt                # main(), SuspendApp, Application.app(module) route registration
├── Api.kt                 # Spine endpoint definitions (see routes-and-validation.md)
├── DomainError.kt         # sealed DomainError hierarchy + toGenericErrorModel()
├── ErrorRoutes.kt         # Route.route(endpoint) { } DSL, error recovery
├── Validation.kt          # accumulate-based input validation shared by all features
├── env/
│   ├── Env.kt              # plain data class config, read from environment variables
│   ├── Dependencies.kt     # ResourceScope wiring: builds every repository/service once
│   ├── persistence.kt      # Hikari + SqlDelight ResourceScope builders
│   └── ktor.kt             # Application.configure(): plugins, JSON, CORS, JWT auth
├── auth/                  # JwtService/JwtConfig — JWT issuing/verification, used across features
├── users/                 # UserPersistence, UserService, UserRoutes
├── articles/              # ArticlePersistence, FavouritePersistence, SlugGenerator, ArticleService, ArticleRoutes
├── profiles/              # ProfileRoutes (reuses UserPersistence)
└── tags/                  # TagPersistence, TagRoutes
```

Keep the boundary explicit per feature:

```
<Feature>Routes -> <Feature>Service -> <Feature>Persistence -> SqlDelight (generated queries) -> JDBC/Hikari
```

- `*Persistence` classes wrap generated SqlDelight `*Queries` objects; they are the only place SQL/`PSQLException`
  handling happens (see `UserPersistence.raiseUniqueViolation`).
- `*Service` classes hold business rules that span persistence calls (validation, ownership checks, composing
  profiles/tags/favorites onto an article). Simple pass-throughs stay in the narrowest `Raise` type; only widen to
  `DomainErrors` when a function genuinely combines multiple error families — see
  `references/routes-and-validation.md`.
- `*Routes` files only decode/encode DTOs and call into a service — no persistence access, no manual error mapping.
- Small features with no extra business logic (`tags`, `profiles`) skip the `*Service` layer entirely: their routes
  call `*Persistence` directly.

## App bootstrap pattern

The app uses [`SuspendApp`](https://arrow-kt.io/learn/coroutines/suspendapp/) (Arrow) instead of a plain `main`, and
`arrow.fx.coroutines.resourceScope` to build and release all resources (Hikari pool, JDBC driver, Netty engine) in
one structured scope, released automatically on shutdown/cancellation:

```kotlin
fun main() = SuspendApp {
    val env = Env()
    resourceScope {
        val dependencies = dependencies(env)
        val _ = server(Netty, host = env.http.host, port = env.http.port) { app(dependencies) }
        awaitCancellation()
    }
}

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

`Application.configure(jwtConfig)` (`env/ktor.kt`) installs the cross-cutting plugins: `DefaultHeaders`,
`ContentNegotiation` (kotlinx.serialization JSON), `CORS`, and JWT `authentication`.

## Dependency wiring — `Dependencies` + `ResourceScope`

There is a single, flat `Dependencies` holder built once in `env/Dependencies.kt`, not a two-level
root/feature-module split — this service is small enough that per-feature wiring would add indirection without
benefit:

```kotlin
class Dependencies(
    val userService: UserService,
    val jwtService: JwtConfig<JwtContext>,
    val articleService: ArticleService,
    val healthCheck: HealthCheckRegistry,
    val tagPersistence: TagPersistence,
    val userPersistence: UserPersistence,
)

suspend fun ResourceScope.dependencies(env: Env): Dependencies {
    val hikari = hikari(env.dataSource)
    val sqlDelight = sqlDelight(hikari)

    val userRepo = UserPersistence(sqlDelight.usersQueries, sqlDelight.followingQueries)
    val articleRepo = ArticlePersistence(sqlDelight.articlesQueries, sqlDelight.commentsQueries, sqlDelight.tagsQueries)
    val tagPersistence = TagPersistence(sqlDelight.tagsQueries)
    val favouritePersistence = FavouritePersistence(sqlDelight.favoritesQueries)

    val jwtService = JwtService(env.auth, userRepo)
    val userService = UserService(userRepo, jwtService)

    val checks = HealthCheckRegistry {
        register(HikariConnectionsHealthCheck(hikari, minConnections = 1))
    }

    return Dependencies(
        userService = userService,
        jwtService = jwtService.config,
        articleService = ArticleService(slugifyGenerator(), articleRepo, userRepo, tagPersistence, favouritePersistence),
        healthCheck = checks,
        tagPersistence = tagPersistence,
        userPersistence = userRepo,
    )
}
```

Rules:

- `dependencies(env)` is a `suspend ResourceScope.() -> Dependencies` extension — anything it needs to close later
  (Hikari pool, JDBC driver) is acquired with `ResourceScope` builders, not created as a plain object.
- Persistence classes are constructed directly from SqlDelight `*Queries` objects generated onto the shared
  `sqlDelight.<table>Queries` instance — there is no repository interface layer to implement.
- Services receive already-built persistence instances through their constructor (explicit wiring); a service that
  needs two repositories (e.g. `ArticleService` needing `UserPersistence` for author profiles) takes both directly
  instead of one repository depending on another.
- `Dependencies` exposes services (`userService`, `articleService`), plus the few persistence instances that have no
  service layer (`tagPersistence`, `userPersistence` for profile lookups) and the health check registry —
  `Main.kt`'s `app(module)` only ever reads from this object, never builds anything itself.

## Resource lifecycle — `ResourceScope`, not `ApplicationStopped`

Resources are released by structured `ResourceScope` cleanup, driven by `resourceScope { }` in `Main.kt`, not by
registering `monitor.subscribe(ApplicationStopped) { }` callbacks:

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
    return SqlDelight(driver, /* adapters */)
}
```

- Use `autoCloseable { }` for `AutoCloseable` resources (Hikari pool) and `closeable { }` for `Closeable` resources
  (JDBC driver). Both register their `close()` with the enclosing `resourceScope`, released in reverse acquisition
  order when the scope exits (including on cancellation of `SuspendApp`).
- `arrow.continuations.ktor.server(Netty, host, port) { app(dependencies) }` is itself a resource acquired inside
  the same `resourceScope`, so the HTTP server shuts down before the database pool it depends on.
- When adding a new external resource (another data source, an HTTP client, a queue connection), add a
  `suspend ResourceScope.build(env: Env.X): X` function next to the others in `env/persistence.kt` (or a new
  `env/<name>.kt`), acquire it inside `dependencies(env)`, and pass the constructed instance into whatever
  persistence/service needs it — do not open resources inside a `*Service`/`*Persistence` constructor.

## Configuration pattern

Configuration is a single plain `Env` data class (`env/Env.kt`) read directly from environment variables with
hardcoded local-dev defaults — there is no `application.yaml`/`ApplicationConfig` loader in this project:

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
}
```

To add a new configuration group:

1. Add a nested `data class` to `Env`, following the `getenv("VAR_NAME") ?: default` pattern with a private
   top-level `const val` for the default.
2. Add it as a constructor parameter (with a default instance) on `Env` itself, mirroring `dataSource`/`http`/`auth`.
3. Thread it through `dependencies(env)` to whatever builder function needs it (`env.newThing`).

## Health checks

`HealthCheckRegistry` (Cohort) is built once in `dependencies(env)` next to the resources it checks (e.g.
`HikariConnectionsHealthCheck(hikari, minConnections = 1)`) and exposed on `Dependencies.healthCheck`. `Main.kt`
installs it with `install(Cohort) { healthcheck("/readiness", module.healthCheck) }`. Add new checks to the same
registry rather than installing a second `Cohort` block.

## Persistence and migrations

- Schema is defined as SqlDelight `.sq` files; `SqlDelight.Schema.create(driver)` applies it on startup
  (`env/persistence.kt`) — there is no separate CI migration pipeline step for this project.
- Column adapters (e.g. `UserId`, `ArticleId` value classes) are defined once in `env/persistence.kt` via the
  `columnAdapter` helper and passed into the generated SqlDelight `*.Adapter` constructors.
