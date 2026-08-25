# Ktor Arrow Wiki / Book progress

## Goal

Turn the current documentation site into a comprehensive, versioned online book for
building Ktor applications with Arrow and functional Kotlin. The RealWorld Conduit
application is the running example, but the material should teach transferable design
principles rather than present its API contract as universally prescriptive.

## Current state

The documentation has three high-quality, advanced tutorials:

- resource-safe bootstrap with `SuspendApp` and `ResourceScope`
- end-to-end typed-error handling with `Raise<DomainError>`
- accumulating request validation with `accumulate`

This is currently a focused tutorial set rather than a complete wiki/book. It has no
reference section despite the tutorials overview promising one, and the MkDocs
navigation exposes only the three tutorial pages.

## Completed

- [x] Correct the homepage health documentation to match the implemented `/readiness`
  endpoint.

## Documentation correctness backlog

- [ ] Keep README, homepage, setup documentation, and deployed application routes in
  sync. The application currently installs only Cohort's `/readiness` endpoint.
- [ ] Add `docs/tutorials/adding-a-tutorial.md` to navigation or move it to contributor
  documentation.
- [x] Add strict documentation builds and link checking to CI.
- [ ] Ensure Kotlin snippets compile against the pinned Kotlin, Arrow, Ktor, and Spine
  versions, preferably by extracting snippets from tested source.
- [ ] Label snippets as exact repository code, simplified teaching code, or pseudocode.

## Proposed information architecture

```text
Home
Getting started
  - Prerequisites and versions
  - Run locally
  - Explore the API
  - Project tour

Part I — Functional Kotlin and Arrow
  - Functional design in a Ktor application
  - Typed errors with Raise
  - Fail-fast validation
  - Accumulating validation
  - Kotlin features used in this codebase

Part II — Building the HTTP application
  - Application bootstrap and resource safety
  - Ktor plugins and serialization
  - API contracts with Spine
  - Routes and request lifecycle
  - Authentication and authorization
  - HTTP error boundaries

Part III — Domain and persistence
  - Feature-first design
  - Domain modelling and value types
  - Services and business rules
  - SQLDelight and PostgreSQL
  - Transactions, constraints, and query performance
  - Adding an end-to-end feature

Part IV — Verification and operations
  - Testing services and typed errors
  - Testing routes with Testcontainers
  - Configuration and secrets
  - Docker, health checks, and graceful shutdown
  - Observability, resilience, and security

Reference
  - Endpoint reference / OpenAPI
  - Error catalogue
  - Configuration reference
  - Package and naming conventions
  - Dependency catalogue
  - Glossary
```

## Prioritised content backlog

### P0 — Foundations and coherent learning path

- [ ] **Functional Kotlin in this application**: immutable data, pure functions, effects,
  boundaries, and why the project uses functional techniques.
- [ ] **Typed errors with Arrow Raise**: `Raise<E>`, `raise`, `ensure`, `ensureNotNull`,
  `recover`, `withError`, `bind`, error widening, and when exceptions are appropriate.
- [ ] **Kotlin features used here**: context parameters, name-based destructuring, value
  classes, `data object`, and experimental Arrow accumulation APIs.
- [ ] **Architecture**: feature-first package rules, functional core / imperative shell,
  dependency direction, and the complete request lifecycle.
- [ ] **Adding an endpoint**: Spine contracts, public/required/optional authentication,
  route rules, parameter extraction, response construction, and the central error
  boundary.
- [ ] **HTTP errors**: RealWorld's `422 GenericErrorModel`, malformed input, auth errors,
  expected domain errors versus unexpected server failures, and the limitations of flat
  string error responses.

### P1 — Teach the patterns already present in the application

- [ ] **Domain modelling**: `NonEmptyList`, validated value classes, patch inputs, and
  making invalid states difficult to represent.
- [ ] **Authorization**: separate authentication from domain authorization; document
  ownership checks such as `NotArticleAuthor` and `NotCommentAuthor`.
- [ ] **Second vertical slice**: article update, comment deletion, or follow/unfollow to
  demonstrate path parameters, authentication, authorization, and composed errors.
- [ ] **SQLDelight persistence**: schemas, generated query APIs, column adapters,
  persistence boundaries, and translating known unique-constraint failures.
- [ ] **Transactions and concurrency**: database invariants, race conditions, multi-write
  operations, and a production migration strategy.
- [ ] **Performance**: pagination, indexes, batched article enrichment, and avoiding N+1
  queries.
- [ ] **JWT**: issuing and verifying claims, `JwtContext`, `Token` authentication,
  optional authentication, expiration, and revocation limitations.

### P1 — Testing and API reference

- [ ] **Testing strategy**: service, route, and database integration test boundaries.
- [ ] **Testing typed errors**: `assertRaised`, nested validation errors, and HTTP mapping
  assertions.
- [ ] **Route testing**: Ktor test server, Spine typed client, authentication helpers, and
  contract assertions.
- [ ] **Testcontainers and fixtures**: PostgreSQL lifecycle, isolation, fixture design,
  and test dependency graphs.
- [ ] **API reference**: link and explain `api/openapi.yml`; provide endpoint/auth/request/
  response/error tables and `curl` examples.

### P2 — Production operation and security

- [ ] **Configuration reference**: document every environment variable, type, default,
  example, and secret classification.
- [ ] **Deployment and lifecycle**: Docker, Compose, `SIGTERM`, graceful shutdown, and
  the difference between Gradle development runs and deployed applications.
- [ ] **Health checks**: define readiness, liveness, and startup semantics. Document only
  the currently implemented readiness route until the others exist.
- [ ] **Observability**: structured logs, request correlation, metrics, tracing, safe
  logging, alerts, and failure reporting.
- [ ] **Resilience**: Hikari sizing, timeouts, blocking JDBC work in coroutines,
  backpressure, and database outage behaviour.
- [ ] **Security posture**: production secrets, password-hashing policy, CORS policy, TLS,
  proxy headers, rate limits, request limits, and dependency maintenance.

## Editorial rules

- Every chapter should state its target audience, prerequisites, learning objectives,
  and next reading.
- Explain trade-offs, not only the local implementation pattern.
- Clearly mark intentional simplifications and production limitations, including
  permissive CORS, local JWT defaults, direct schema creation, and RealWorld's error
  schema.
- Keep the OpenAPI contract and source code as the authoritative sources for API
  behaviour.

## Next implementation tasks

1. Add the P0 Arrow typed-errors foundations chapter and navigation section.
2. Add the P0 Ktor route, authentication, and HTTP error-boundary chapters.
3. Add a reference section for API, errors, configuration, and package conventions.
4. Add documentation validation to CI.
