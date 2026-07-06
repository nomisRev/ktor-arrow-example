# Routes and Validation

This project defines its HTTP contracts with [Spine](https://gitlab.com/opensavvy/spine) instead of raw Ktor
`routing { }` blocks, and models failures as `Raise<DomainError>` (Arrow) instead of exceptions. Keep that
combination in mind whenever you add or change an endpoint.

## Define the contract in `Api.kt`

Every endpoint is declared once, as data, in `Api.kt`. Route handlers do not decide status codes or paths — they
implement an already-declared `Endpoint`.

- The root object extends `opensavvy.spine.api.RootResource` (aliased `SpineRootResource`).
- Nested resources are `StaticResource<Parent>("segment", Parent)` for fixed path segments, or
  `DynamicResource<Parent>("name", Parent)` for path parameters (`:name`).
- Each endpoint is a `by` delegate built from `get()`, `post()`, `put()`, `delete()`, optionally chained with:
  - `.request<Body>()` — expected JSON request body.
  - `.parameters(::MyParameters)` — typed query parameters (see below).
  - `.response<Body>()` — success response body.
  - `.failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)` — the error payload/status this project
    always uses for domain failures.

```kotlin
object Articles : StaticResource<Api>("articles", Api) {
    val list by
        get()
            .parameters(::ArticlesParameters)
            .response<MultipleArticlesResponse>()
            .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)

    object Slug : DynamicResource<Articles>("slug", Articles) {
        val update by
            put()
                .request<ArticleWrapper<UpdateArticle>>()
                .response<SingleArticleResponse>()
                .failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)
    }
}
```

Every endpoint in this codebase fails the same way: `GenericErrorModel` at `422 Unprocessable Entity`. Do not invent
per-endpoint error payloads — map new error cases into `GenericErrorModel` instead (see below).

## Wiring a route handler

Route files (`UserRoutes.kt`, `ArticleRoutes.kt`, ...) implement each `Endpoint` with the `route(endpoint) { ... }`
DSL from `io.github.nomisrev.route` (`ErrorRoutes.kt`), not the one from `opensavvy.spine.server` directly:

```kotlin
fun Route.userRoutes(userService: UserService, jwtService: JwtConfig<JwtContext>) {
    route(Api.Users.register) {
        val (username, email, password) = body.user
        val token = userService.register(RegisterUser(username, email, password))
        respond(UserWrapper(User(email, token.value, username, "", "")), HttpStatusCode.Created)
    }
}
```

Inside the block:

- `body` is the decoded, typed request (from `.request<...>()`).
- `parameters`/`idOf(Resource)` give typed query/path parameters.
- `respond(dto, status)` writes the typed response declared with `.response<...>()`.
- Call service/repository functions directly — there is no `try/catch`. Domain failures `raise` a `DomainError`
  and are handled once, generically, by the `route` wrapper.
- Wrap authenticated groups with `authenticateWith(jwtService) { ... }` (or `.orAnonymous()` for endpoints that
  behave differently for guests vs. logged-in users) and read `call.principal`.

## `ErrorRoutes.kt`: how failures become HTTP responses

`Route.route(endpoint) { block }` in `ErrorRoutes.kt` runs `block` inside `arrow.core.raise.recover` with the
handler's body scoped to `context(Raise<DomainError>)`:

```kotlin
inline fun <...> Route.route(
    endpoint: Endpoint<In, Out, Failure, Params>,
    crossinline block: suspend context(Raise<DomainError>) TypedResponseScope<...>.() -> Unit,
): Unit =
    route(endpoint) response@{
        recover(
            block = { block() },
            recover = { error: DomainError -> fail(error.toGenericErrorModel()) },
        )
    }
```

This is the **only** place that converts a raised `DomainError` into an HTTP response, via
`DomainError.toGenericErrorModel()` in `DomainError.kt`. Route bodies never call `fail`/`respond` for error cases
themselves. When you add a new `DomainError` subtype, add a matching branch to `toGenericErrorModel` — it is an
exhaustive `when`, so the compiler forces you to handle it.

## `DomainError`: fine-grained errors that grow into `DomainError`

`DomainError` is the top-level `sealed interface`. Every concrete error is grouped under a feature-specific sealed
interface that extends it:

```kotlin
sealed interface DomainError

sealed interface ValidationError : DomainError
data class IncorrectInput(val errors: NonEmptyList<InvalidField>) : ValidationError
data class MissingParameter(val name: String) : ValidationError
// ...

sealed interface UserError : DomainError
data class UserNotFound(val property: String) : UserError
data class EmailAlreadyExists(val email: String) : UserError
data object PasswordNotMatched : UserError

sealed interface ArticleError : DomainError
data class ArticleBySlugNotFound(val slug: String) : ArticleError
data class NotArticleAuthor(val userId: Long, val slug: String) : ArticleError
// ...
```

**Rule: declare the narrowest `Raise` context a function actually needs.** Persistence and small helper functions
`raise` only the errors they can actually produce, e.g.:

```kotlin
// UserPersistence.kt — can only fail with a UserError
context(_: Raise<UserError>)
fun verifyPassword(email: String, password: String): UserIdAndInfo { ... }

context(_: Raise<UserNotFound>)
fun select(userId: UserId): UserInfo { ... }
```

Because `UserError`, `ArticleError`, `ValidationError`, etc. are all subtypes of `DomainError`, and Arrow's
`context(Raise<E>)` is contravariant in the way it composes, a function written as `context(_: Raise<DomainError>)`
can call any function that raises a narrower error type directly — no wrapping, no `mapLeft`, no manual lifting.
This is how the error type **grows** as you move up the call stack:

```kotlin
class UserService(private val repo: UserPersistence, private val jwtService: JwtService) {
    // register only fails with UserError (repo.insert) plus IncorrectInput (validate) -> DomainError
    context(_: Raise<DomainError>)
    fun register(input: RegisterUser): JwtToken {
        val (username, email, password) = input.validate()   // Raise<IncorrectInput>
        val userId = repo.insert(username, email, password)  // Raise<UserError>
        return jwtService.generateJwtToken(userId)            // Raise<JwtError>
    }

    // getUser only ever needs UserNotFound — keep that narrow context, do not widen unnecessarily
    context(_: Raise<UserNotFound>)
    fun getUser(userId: UserId): UserInfo = repo.select(userId)
}
```

Guidelines:

- Repository/persistence functions: narrowest possible error type (`UserError`, `ArticleError`, a single
  variant like `UserNotFound`, ...).
- Service functions: `Raise<DomainError>` **only when they genuinely combine multiple error families** (validation
  + persistence + JWT, etc.). If a service function only ever delegates to one narrow-error repository call, keep
  that narrow type instead of widening to `DomainError` for no reason.
- Route handlers (the `route(endpoint) { ... }` block body): always `context(Raise<DomainError>)` — this is the
  edge of the service, where any remaining domain error must be convertible to `GenericErrorModel` via
  `toGenericErrorModel`.
- Never introduce exceptions for expected failures. `raise`/`ensure`/`ensureNotNull`/`catch` (Arrow) are the only
  vocabulary for expected error paths; reserve real exceptions (letting them propagate) for truly unexpected
  failures (e.g. an unmapped `PSQLException`).

## Validation: `accumulate` in `Validation.kt`

Input validation never short-circuits on the first failing field — it must report every invalid field (and every
rule broken within a field) in one response. This is done with Arrow's experimental accumulation API
(`arrow.core.raise.context.accumulate`), not manual `NonEmptyList` building.

There are two accumulation levels, nested:

1. **Field level** — rules for a single `String`/`Int` accumulate into `NonEmptyList<String>` messages, using
   `RaiseAccumulate<String>` and `ensureOrAccumulate`:

   ```kotlin
   context(_: Raise<NonEmptyList<String>>)
   private fun String.passwordRules(): String = accumulate {
       notBlank()
       minSize(MIN_PASSWORD_LENGTH)
       maxSize(MAX_PASSWORD_LENGTH)
       this@passwordRules
   }

   context(_: RaiseAccumulate<String>)
   private fun String.notBlank(): String = also {
       val _ = ensureOrAccumulate(isNotBlank()) { "Cannot be blank" }
   }
   ```

   `withError(::InvalidPassword)` then wraps that `NonEmptyList<String>` into a single `InvalidField`
   (`InvalidPassword`), attaching the field name:

   ```kotlin
   context(_: Raise<InvalidField>)
   private fun String.validPassword(): String = passwordValidation()

   context(_: Raise<InvalidField>)
   private fun String.passwordValidation(): String = withError(::InvalidPassword) { passwordRules() }
   ```

2. **Object level** — each field of an input DTO is validated independently and accumulated with
   `val x by accumulating { ... }`, so that failures in `username`, `email`, and `password` are all collected
   before raising, instead of stopping at the first one:

   ```kotlin
   context(_: Raise<IncorrectInput>)
   fun RegisterUser.validate(): RegisterUser =
       withError(::IncorrectInput) {
           accumulate {
               val username by accumulating { username.validUsername() }
               val email by accumulating { email.validEmail() }
               val password by accumulating { password.validPassword() }
               RegisterUser(username, email, password)
           }
       }
   ```

   Here `accumulate { }` collects into `NonEmptyList<InvalidField>`, and the outer `withError(::IncorrectInput)`
   turns that list into the single `IncorrectInput : ValidationError : DomainError` that routes/services raise.

Patterns to follow when adding a new validated input:

- Define one `InvalidField` subtype per field (`InvalidTitle`, `InvalidTag`, ...), carrying
  `errors: NonEmptyList<String>` and a fixed `field` name — this is what ends up in the `GenericErrorModel` body
  (`"$field: ${errors.joinToString()}"`).
- Write small, reusable `RaiseAccumulate<String>` rule functions (`notBlank`, `minSize`, `maxSize`,
  `looksLikeEmail`) and compose them inside a field's `accumulate { }` block.
- Expose a single `context(_: Raise<InvalidField>) fun T.validXxx(): T` per field, and a
  `context(_: Raise<IncorrectInput>) fun Dto.validate(): Dto` per input DTO that accumulates all its fields and
  reconstructs the (now-validated) DTO.
- Lists validate with `mapOrAccumulate` (see `List<String>.validTags()`); optional fields validate with
  `field?.validXxx()` inside the same `accumulating { }` block so `null` short-circuits without raising.
- Query parameters (`ArticlesParameters`, `FeedParameters` in `ArticleRoutes.kt`) validate the same way, right next
  to their `Parameters` class, producing the plain input type the service expects (`GetArticles`, `GetFeed`).
