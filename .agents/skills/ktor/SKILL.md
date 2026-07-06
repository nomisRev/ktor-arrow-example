---
name: ktor
description: >
  Build and modify Ktor services in the Foodies repository using the established project conventions.
  Use when working in Ktor modules for tasks involving Ktor app/module wiring, route-service-repository design.
---

# Ktor Service Skill

Apply these conventions when implementing or reviewing Ktor code in this repository. Load only the references needed for the current task.

## Service architecture and wiring

Read [references/service-architecture.md](references/service-architecture.md) when changing application bootstrap (`Main.kt`/`SuspendApp`), `Env` configuration, `Dependencies` wiring, or `ResourceScope`-based resource lifecycle.

## Package structure

Read [references/package-structure.md](references/package-structure.md) when creating, moving, or reorganizing files within a service module. Follow domain-driven feature packages, not technical layers.

## Routes and validation

Read [references/routes-and-validation.md](references/routes-and-validation.md) when editing HTTP contracts (Spine `Api.kt` endpoints), route handlers, `DomainError` modelling/mapping, or `accumulate`-based validation.
