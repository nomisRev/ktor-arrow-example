# Package Structure

Organize packages by **domain feature**, not by technical layer. Group the vertical slice (routes, service, repository/client/wrapper,
domain model) for each feature together in one package.

## Guiding principles

- **Feature-first**: top-level packages inside a service represent domain features or sub-domains, not layers like
  `routes/`, `service/`, `repository/`, `client/`.
- **Feature-prefixed file names**: use `CatalogRoutes.kt`, `CatalogService.kt` — not generic `Routes.kt`, `Service.kt`.
  Avoids ambiguous IDE tabs.
- **Split interfaces per feature**: when a service has multiple features, split the service interface so each feature
  owns its contract.
- **Split repositories per feature**: each feature defines its own repository interface **and its own Exposed repository
  implementation**. Do not create a single shared `ExposedXxxRepository` that implements multiple feature interfaces.
- **Shared repository for cross-cutting operations**: when multiple features need the same operations on a shared
  aggregate (e.g. `findById`, `update`), extract a shared repository interface and implementation into `persistence/`.
  See [Shared repository pattern](#shared-repository-pattern).
- **Exposed table objects in `persistence/`**: Exposed `object` table definitions are shared infrastructure. They live
  in `persistence/<Aggregate>Tables.kt`. Feature repositories import from `persistence/` — they do not own or duplicate
  table definitions.
- **Third-party wrappers stay close to usage**: place SDK wrappers in the owning feature package by default.
  Extract to a shared package named by capability/vendor (for example `launchdarkly/`) only when reused across features.
- **Per-feature event publishers**: each feature publishes only the events it owns.
- **Per-feature dependency wiring**: you can wire a feature in an optional `<Feature>Module.kt`, or wire it directly
  in root `<Service>Module.kt` for simpler services. In both cases, root module output should be least-powerful and
  route-facing (services/consumers/health checks), not repositories.
- **Shared domain models at root**: domain models used across features live in `Model.kt` at the service root package.
- **Bootstrap at root**: `<Service>App.kt` and `<Service>Module.kt` stay at the service root. `<Service>Module.kt` is a
  thin orchestrator that calls feature module functions.
- **Config split**: app-level config (`host`, `port`, `telemetry`, `database`, `rabbitmq`) stays in root `Config.kt`.
  Feature-specific config lives in the feature package.
- **Tests mirror main**: test packages mirror the main source tree exactly.

## When to apply feature packages

Apply feature-based sub-packages when a service has **multiple distinct responsibilities** or sub-domains (typically 2+
independent route groups, separate event flows, or distinct business processes).

Keep a **flat structure** when a service has a single aggregate with one set of operations, or is too small to benefit
from sub-packages (< 5 source files).

## Feature-packaged service structure

```
io.ktor.foodies.<service>/
├── <Service>App.kt                        # Entrypoint — calls module(), then app()
├── <Service>Module.kt                     # Thin orchestrator: calls featureAModule(), featureBModule(), exposes minimal route deps
├── Config.kt                              # App-level config (host, port, telemetry, db, rabbitmq)
├── Model.kt                               # Domain models shared across features
├── persistence/                            # Optional: only when the module owns persistence
│   ├── <Aggregate>Tables.kt              # Exposed table objects (shared infrastructure)
│   └── <Aggregate>Repository.kt          # Shared repository interface + impl (findById, update)
├── <integration-name>/                   # Optional shared wrapper, only if reused by multiple features
│   ├── <IntegrationName>Service.kt
│   └── SdkProvider<IntegrationName>Service.kt
├── <featureA>/
│   ├── <FeatureA>Module.kt               # Optional: wires feature slice (repo/client/wrapper, service, publisher, routes)
│   ├── <FeatureA>Routes.kt               # HTTP endpoints for this feature
│   ├── <FeatureA>Service.kt              # Business logic
│   ├── <FeatureA>Repository.kt           # Optional: interface for feature-specific data access
│   ├── Exposed<FeatureA>Repository.kt    # Optional: Exposed impl, imports from persistence/
│   ├── <FeatureA>Client.kt               # Optional: interface for downstream service communication
│   ├── Http<FeatureA>Client.kt           # Optional: transport implementation for downstream calls
│   ├── <FeatureA>FeatureFlagService.kt   # Optional: feature-local wrapper for third-party SDK usage
│   ├── <FeatureA>EventPublisher.kt       # Publishes only this feature's events
│   └── <FeatureA>Requests.kt             # Request/response DTOs
├── <featureB>/
│   ├── <FeatureB>Module.kt               # Optional
│   ├── <FeatureB>Routes.kt
│   ├── <FeatureB>Service.kt
│   ├── <FeatureB>Repository.kt           # Optional
│   ├── Exposed<FeatureB>Repository.kt    # Optional: Exposed implementation
│   ├── <FeatureB>Client.kt               # Optional: downstream service boundary
│   ├── Http<FeatureB>Client.kt           # Optional: downstream Ktor implementation
│   ├── <FeatureB>EventPublisher.kt
│   ├── <FeatureB>EventConsumer.kt        # Message consumer (if applicable)
│   └── handlers/                          # Event handlers (if many)
│       └── <EventName>Handler.kt
└── <admin>/                               # Cross-cutting feature that composes from other features
    ├── <Admin>Module.kt                   # Imports services from other features
    └── <Admin>Routes.kt
```

## Flat service structure

```
io.ktor.foodies.<service>/
├── <Service>App.kt
├── <Service>Module.kt
├── Config.kt
├── Domain.kt                              # Domain models + DTOs + validation
├── Routes.kt
├── Service.kt
├── Repository.kt                          # Optional: for persistence-owning modules
├── <Dependency>Service.kt                 # Optional: for downstream service communication
├── Http<Dependency>Service.kt             # Optional: downstream transport implementation
├── <integration-name>/                    # Optional: shared wrapper only if reused
│   ├── <IntegrationName>Service.kt
    └── SdkProvider<IntegrationName>Service.kt
└── events/                                # (if applicable)
    └── <EventName>Handler.kt
```

### Decision heuristic: where does an operation belong?

| Question                                                       | Answer                                                                                                     |
|----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| Is this a DB operation used by 2+ features?                    | Put the interface + impl in `persistence/`.                                                                |
| Is this a DB operation specific to one feature?                | Put it in that feature's repository interface and `Exposed<Feature>Repository`.                           |
| Is this a downstream service call needed by one feature?       | Put it in that feature package as `<Feature>Client`/`Http<Feature>Client` (or `<Dependency>Service`).    |
| Is this third-party wrapper used by one feature only?          | Keep it in that feature package next to the service using it.                                              |
| Is this third-party wrapper reused by multiple features?       | Extract to a named shared package such as `launchdarkly/`, not a generic `integrations/` package.        |
| Does a table have a FK to another table?                       | Both tables stay in `persistence/` — the FK creates a compile-time dependency.                            |
| Is a table only referenced by one feature's repository?        | It still lives in `persistence/` — table objects are always shared infrastructure.                        |
