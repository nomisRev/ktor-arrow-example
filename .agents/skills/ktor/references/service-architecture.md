# Service Architecture

Use the existing Foodies Ktor layout and manual dependency wiring.

## Module structure

Typical service package (`src/main/kotlin/io/ktor/foodies/<service>/`) contains a mix of these elements, depending on the module type:

- `<Service>App.kt`: entrypoint and Ktor plugin setup
- `Config.kt`: `@Serializable` config types loaded from `application.yaml`
- `<Service>Module.kt`: dependency graph assembly
- `Routes.kt`: HTTP route definitions
- `Service.kt`: business logic layer
- `Repository.kt`: data access layer (for services that own persistence)
- `<Dependency>Service.kt`: downstream service communication boundary
- `<vendor-or-capability>/`: named package for shared third-party wrappers (for example `featureflags/` for `LaunchDarkly`)

Keep the boundary explicit:

```
Routes -> Service -> XXXRepository -> Exposed
                  -> XXXClient -> Ktor / VendorSdk / ...
```

## App bootstrap pattern

Use `ApplicationConfig("application.yaml").getAs<Config>()`, then start Netty.

```kotlin
fun main() {
    val config = ApplicationConfig("application.yaml").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val (_, openTelemetry) = monitoring(config.telemetry)
        app(module(config, openTelemetry))
    }.start(wait = true)
}
```

`app(module)` should install core plugins and register routes.

## Dependency wiring — root or two-level pattern

For services with feature packages, wiring can be split across two levels, or done directly in root module when that is
clearer.

**Root `<Service>Module.kt`** — thin orchestrator. Creates shared infrastructure, then delegates to feature module
functions:

```kotlin
fun Application.module(config: Config, telemetry: OpenTelemetry): OrderModule {
    val dataSource = dataSource(config.database, telemetry)

    val httpClient = buildHttpClient(telemetry)

    monitor.subscribe(ApplicationStopped) { httpClient.close() }
  
    val placement = placementModule(config, dataSource, publisher, httpClient)
    val tracking = trackingModule(config, dataSource, publisher)
    val fulfillment = fulfillmentModule(config, dataSource, publisher, subscriber)
    val admin = adminModule(tracking, fulfillment)

    val readinessCheck = buildReadinessChecks(dataSource, rabbitChannel)
    return OrderModule(placement, tracking, fulfillment, admin, readinessCheck)
}
```

**Feature `<Feature>Module.kt`** — owns wiring for that vertical slice only:

```kotlin
// placement/PlacementModule.kt
fun placementModule(
    config: Config,
    dataSource: FoodiesDataSource,
    httpClient: HttpClient,
): PlacementModule {
    val repo = ExposedPlacementRepository(dataSource.database)
    val basketClient = HttpBasketClient(httpClient, config.basket.baseUrl)
    val service = DefaultPlacementService(repo, basketClient, eventPublisher, config.order)
    return PlacementModule(service)
}
```

Rules:

- Shared infrastructure (data source, rabbit channel, HTTP client) is created **once** in the root module and passed
  into feature module functions.
- Feature module functions create only their own repository/client/wrapper, service, publisher, and consumer instances.
- When a service needs capabilities from multiple repositories, inject those repositories separately (explicit wiring)
  instead of repository-interface inheritance.
- The root `<Service>Module` data class should expose only what `app(...)` needs (least powerful): either assembled
  feature modules or narrower dependencies such as services, consumers, and health checks.
- Route wiring in `<Service>App.kt` should depend on services, not repositories.
- Close shared resources (rabbit channel, HTTP client) in the root `ApplicationStopped` handler only.

Small modules can keep all wiring in a single root module when splitting into feature sub-modules does not add clarity.

## Configuration pattern

- Group related configuration under a new root key in `application.yaml` i.e. server, database, rabbit, etc.
- Support environment overrides (`"$ENV:default"` style).
- Model nested settings as `@Serializable` nested data classes.

### Adding new configuration

To add a new config value:

1. Add a key to `application.yaml`, either at root or under an existing group. Grouping under a dedicated root key (e.g. `data_source`) is preferred — it loads cleanly as a nested `@Serializable` type.
2. Define a `@Serializable` data class for any nested structure.
3. Add the field to the corresponding `Config` (or nested) class.

Example — adding database connection settings:

```yaml
server:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8080"

data_source:
    url: "$DB_URL:jdbc:postgresql://localhost:5432/foodies-database"
    username: "$DB_USERNAME:foodies_admin"
    password: "$DB_PASSWORD:foodies_password"
```

```kotlin
@Serializable
data class Config(
    val server: Server,
    @SerialName("data_source") val dataSource: DataSource,
) {
    @Seriazable
    data class Server(val host: String, val port: Int)
    
    @Serializable
    data class DataSource(val url: String, val username: String, val password: String)
}

val config = ApplicationConfig("application.yaml").getAs<Config>()
```

## Resource lifecycle

Close external resources (database connections, HTTP clients, RabbitMQ channels) via `monitor.subscribe(ApplicationStopped)`. Register closers in the root module so ordering is explicit and centralised:

```kotlin
fun Application.hikari(config: Config): HikariDataSource {
    val hikari = HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.dataSource.url
        username = config.dataSource.username
        password = config.dataSource.password
    })
    monitor.subscribe(ApplicationStopped) { hikari.close() }
    return hikari
}
```

Register finalizers in dependency order so that consumers (services, routes) are torn down before the resources they use.

## Why manual dependency wiring?

Manual wiring forces explicit reasoning about startup order and resource ownership. It makes parallelising initialisation with `Deferred` straightforward, and keeps the dependency graph visible in one place without framework magic. The perceived boilerplate is low in practice — this project demonstrates that.

## Persistence and migrations

- Database migrations run in the CI deployment pipeline.
- Keep SQL schema evolution in module-local migration files.
