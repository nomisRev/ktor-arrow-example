# Ktor Arrow Example

A production-style backend built with Ktor and Arrow, featuring typed errors, resource-safe wiring, and functional Kotlin.

---

## Technology Stack

| Concern | Library |
|---|---|
| HTTP server | [Ktor](https://ktor.io/) (Netty engine) + [Spine](https://opensavvy.dev/open-source/spine/) |
| Functional core & typed errors | [Arrow](https://arrow-kt.io/) (`Raise<DomainError>`) |
| Resource management | [SuspendApp](https://arrow-kt.io/ecosystem/suspendapp/) + Arrow Fx `ResourceScope` |
| Persistence | [SqlDelight](https://sqldelight.github.io/sqldelight/) with PostgreSQL and HikariCP |
| Authentication | [Ktor Auth JWT](https://ktor.io/docs/server-jwt.html) + [kJWT](https://github.com/nefilim/kjwt) |
| Health checks | [Cohort](https://github.com/sksamuel/cohort) (`/healthz/startup`, `/healthz/liveness`, `/healthz/readiness`) |
| Testing | [Kotest](https://kotest.io/) assertions, [TestBalloon](https://github.com/infix-de/testBalloon), [Testcontainers](https://testcontainers.com/) |

## Architecture

The project uses feature-first packages with a thin route layer on top of services and persistence:

```text
src/main/kotlin/io/github/nomisrev/
  Main.kt          SuspendApp entry point, resource-safe server bootstrap
  env/             Env configuration, Dependencies wiring, Ktor setup
  auth/            JWT authentication
  users/           User registration, login, update
  articles/        Articles, comments, favorites, slug generation
  profiles/        Follow / unfollow, profile lookup
  tags/            Tags
```

Each feature follows the same pattern:

1. **`*Routes.kt`** -- request handling and validation, no business logic
2. **`*Service.kt`** -- business logic, errors modeled as `Raise<DomainError>`
3. **`*Persistence.kt`** -- SqlDelight-backed data access

## Quick Start

```shell
docker-compose up -d
./gradlew run
curl -i 0.0.0.0:8080/healthz/readiness
```

!!! warning
    `./gradlew run` does not properly run JVM shutdown hooks, so the port may remain bound after stopping.

---

[Full setup guide](running-the-project.md){ .md-button .md-button--primary }
[View on GitHub](https://github.com/nomisRev/ktor-arrow-example){ .md-button target=_blank }
