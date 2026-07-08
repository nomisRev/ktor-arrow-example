# Validation with typed errors

This tutorial walks through `Validation.kt`, the file responsible for validating every
incoming request in this service. It demonstrates how Arrow's `accumulate` pattern
collects _all_ failures in a single pass instead of stopping at the first one, and how
that pattern slots directly into the ordinary `Raise` DSL used elsewhere in the
application.

The code uses `@OptIn(ExperimentalRaiseAccumulateApi::class)` because the
`accumulate` / `RaiseAccumulate` API is still being stabilised in Arrow. The shape is
unlikely to change, but the annotation is required for now.

---

## The error model

Before looking at validation rules, it helps to understand the types that failures flow
into.

```kotlin
sealed interface InvalidField {
    val errors: NonEmptyList<String>   // (1)
    val field: String
}

data class InvalidEmail(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "email"
}

data class InvalidPassword(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "password"
}

// … InvalidUsername, InvalidTitle, InvalidBody, etc.
```

1. `NonEmptyList<String>` guarantees that if a field is invalid, there is **at least one**
   human-readable message explaining why. This is the central invariant that makes
   accumulation safe: you can never produce an empty error list.

At the top of the hierarchy sits `IncorrectInput`, defined in `DomainError.kt`:

```kotlin
data class IncorrectInput(val errors: NonEmptyList<InvalidField>) : ValidationError
```

Again a `NonEmptyList` — if an `IncorrectInput` exists, at least one field failed.

This nesting (`IncorrectInput` → `NonEmptyList<InvalidField>` → each field carries its
own `NonEmptyList<String>`) precisely mirrors the structure of a validation error
response: multiple fields can fail, and each field can have multiple reasons.

---

## Leaf-level rules: `RaiseAccumulate` and `ensureOrAccumulate`

The lowest level of the stack is individual string predicates. Each one runs inside a
`RaiseAccumulate<String>` context — a specialised `Raise` that _accumulates_ failures
rather than short-circuiting.

```kotlin
context(_: RaiseAccumulate<String>)
private fun String.notBlank(): String = also {
    val _ = ensureOrAccumulate(isNotBlank()) { "Cannot be blank" }
}

context(_: RaiseAccumulate<String>)
private fun String.minSize(size: Int): String = also {
    val _ = ensureOrAccumulate(length >= size) { "is too short (minimum is $size characters)" }
}

context(_: RaiseAccumulate<String>)
private fun String.maxSize(size: Int): String = also {
    val _ = ensureOrAccumulate(length <= size) { "is too long (maximum is $size characters)" }
}

context(_: RaiseAccumulate<String>)
private fun String.looksLikeEmail(): String = also {
    val _ = ensureOrAccumulate(emailPattern.matches(this)) { "'$this' is invalid email" }
}
```

`ensureOrAccumulate` is the accumulating counterpart of `ensure`. Whereas `ensure`
immediately short-circuits the entire computation, `ensureOrAccumulate` records the
error message in the accumulator and lets execution continue. This means every rule in
the same `accumulate` block runs, regardless of how many previous rules have already
failed.

The `@IgnorableReturnValue` annotation signals that the return value of each rule (the
original `String`) is only there to enable fluent chaining; callers that invoke the
function for its side-effect on the accumulator are not required to use it.

---

## Field-level validation: grouping rules with `accumulate`

Individual rules are combined into per-field validators using `accumulate`. The
`accumulate { … }` block creates a `RaiseAccumulate` scope. Inside it you can call
`ensureOrAccumulate` directly (as above), or call any function that requires
`RaiseAccumulate` in context.

```kotlin
context(_: Raise<NonEmptyList<String>>)
private fun String.passwordRules(): String = accumulate {
    notBlank()
    minSize(MIN_PASSWORD_LENGTH)
    maxSize(MAX_PASSWORD_LENGTH)
    this@passwordRules
}
```

`accumulate` itself lives in a `Raise<NonEmptyList<E>>` context: it collects every
individual `String` raised by `ensureOrAccumulate` and, if any were collected, raises
them all together as a `NonEmptyList<String>`. If none were collected the last
expression in the block is returned as the success value.

The field-level validator then wraps the rule set with `withError` to map
`NonEmptyList<String>` onto the concrete `InvalidField` subtype:

```kotlin
context(_: Raise<InvalidField>)
private fun String.passwordValidation(): String =
    withError(::InvalidPassword) { passwordRules() }
```

`withError` (from Arrow) transforms the error type of an inner `Raise` block. Here it
converts a `NonEmptyList<String>` produced by `passwordRules()` into an
`InvalidPassword`. See the
[Arrow docs on transforming errors](https://arrow-kt.io/learn/typed-errors/working-with-typed-errors/#transforming-errors)
for the general pattern.

The public surface of each field validator exposes the `InvalidField` supertype so all
fields can be treated uniformly during object-level accumulation:

```kotlin
context(_: Raise<InvalidField>)
private fun String.validPassword(): String = passwordValidation()
```

---

## Object-level validation: the `accumulate` / `by accumulating` pattern

This is where the design comes together. Each domain object — `RegisterUser`, `Login`,
`NewArticle`, etc. — is validated by an `accumulate` block that collects one
`InvalidField` per field via `by accumulating { … }` delegation:

```kotlin
context(_: Raise<IncorrectInput>)
fun RegisterUser.validate(): RegisterUser =
    withError(::IncorrectInput) {
        accumulate {
            val username by accumulating { username.validUsername() }
            val email    by accumulating { email.validEmail() }
            val password by accumulating { password.validPassword() }
            RegisterUser(username, email, password)
        }
    }
```

Breaking this down:

- **`accumulate { … }`** opens an accumulation scope whose error type is
  `NonEmptyList<InvalidField>`.
- **`by accumulating { … }`** runs the lambda inside the accumulation scope. If it
  raises an `InvalidField`, that error is recorded. Either way, execution continues to
  the next field. The `by` keyword (property delegation) is required here because
  `accumulating` cannot return a value eagerly when a failure has been recorded — the
  delegate defers reading the value until the end of the block, at which point Arrow
  knows whether all fields succeeded.
- The **trailing constructor call** `RegisterUser(username, email, password)` is only
  reached when _all_ delegates have resolved successfully. If any field failed, the
  collected `NonEmptyList<InvalidField>` is raised instead.
- The outer **`withError(::IncorrectInput)`** converts that `NonEmptyList<InvalidField>`
  into the top-level `IncorrectInput` error that the rest of the application works with.

Contrast this with a naïve sequential approach:

```kotlin
// Short-circuits at the first failure — the caller never learns about the others.
context(_: Raise<IncorrectInput>)
fun RegisterUser.validateNaive(): RegisterUser {
    val username = username.validUsername()  // raises immediately on failure
    val email    = email.validEmail()
    val password = password.validPassword()
    return RegisterUser(username, email, password)
}
```

With `accumulate` + `by accumulating`, all three fields are checked in one pass, and
the caller receives every problem at once — which is exactly what an API consumer needs
to correct a form submission.

See the
[Arrow validation docs](https://arrow-kt.io/learn/typed-errors/validation/#fail-first-vs-accumulation)
for a side-by-side comparison of fail-first vs. accumulation.

---

## Nullable fields: accumulating optional values

`Update` models a partial user profile edit where every field is optional:

```kotlin
context(_: Raise<IncorrectInput>)
fun Update.validate(): Update =
    withError(::IncorrectInput) {
        accumulate {
            val username by accumulating { username?.validUsername() }
            val email    by accumulating { email?.validEmail() }
            val password by accumulating { password?.validPassword() }
            Update(userId, username, email, password, bio, image)
        }
    }
```

The `?.` safe-call means that a missing field is simply `null` and passes through
without raising anything. Only fields that are _present_ and _invalid_ contribute errors
to the accumulator.

---

## Collection validation: `mapOrAccumulate`

Tags on an article are validated as a collection. Each tag is checked individually and
all invalid tags are reported together:

```kotlin
context(_: Raise<InvalidField>)
private fun List<String>.validTags(): Set<String> =
    withError(::InvalidTag) { mapOrAccumulate { it.trim().notBlank() }.toSet() }
```

`mapOrAccumulate` is the accumulating counterpart of `map`. It runs the lambda for every
element, collects any raised `String` errors, and — if any were collected — raises them
together as a `NonEmptyList<String>`. The outer `withError(::InvalidTag)` wraps the
whole list's errors into a single `InvalidTag`.

Note that `mapOrAccumulate` still treats the _list_ as an atomic field: one `InvalidTag`
is raised that contains the accumulated messages from all bad tags. This is intentional
— from the API's perspective, "tags" is a single field.

For a deeper look at accumulating over collections see the
[Arrow typed-errors guide](https://arrow-kt.io/learn/typed-errors/working-with-typed-errors/#accumulating-errors).

---

## Value class validation: query parameters

Feed pagination parameters are validated into value classes before being passed to
services:

```kotlin
context(_: Raise<InvalidFeedOffset>)
fun Int.validFeedOffset(): FeedOffset =
    withError(::InvalidFeedOffset) {
        accumulate {
            minSize(MIN_FEED_OFFSET)
            FeedOffset(this@validFeedOffset)
        }
    }

context(_: Raise<InvalidFeedLimit>)
fun Int.validFeedLimit(): FeedLimit =
    withError(::InvalidFeedLimit) {
        accumulate {
            minSize(MIN_FEED_LIMIT)
            FeedLimit(this@validFeedLimit)
        }
    }
```

`@JvmInline value class FeedOffset(val offset: Int)` and `FeedLimit` are declared in
`ArticleRoutes.kt`. Wrapping the raw `Int` in a value class makes it impossible to
accidentally pass an unvalidated offset where a validated one is expected — the type
system enforces the invariant at compile time.

The two validated parameters are then accumulated at the `FeedParameters` level:

```kotlin
context(_: Raise<IncorrectInput>)
fun FeedParameters.validate(userId: UserId): GetFeed =
    withError(::IncorrectInput) {
        accumulate {
            val offset by accumulating { offset.validFeedOffset() }
            val limit  by accumulating { limit.validFeedLimit() }
            GetFeed(userId, limit.limit, offset.offset)
        }
    }
```

---

## How validation flows into the `Raise` DSL

Every public `validate()` function requires `Raise<IncorrectInput>` in context —
the same error type used throughout the service layer. This means calling `.validate()`
inside any `Raise<IncorrectInput>` scope costs nothing extra; it is just a function
call:

```kotlin
// Inside a route handler, already inside a Raise<DomainError> / Raise<IncorrectInput> scope:
val body = call.receive<RegisterUser>()
val validated = body.validate()       // accumulates and either raises or returns
userService.register(validated)       // only reachable with a fully validated value
```

There is no impedance mismatch between the accumulating validation layer and the
fail-first service layer. Once `validate()` returns successfully you have a plain
`RegisterUser` value; any subsequent `Raise`-based code treats it exactly like any
other typed-error computation.

This is the key insight: **accumulation is scoped**. The `accumulate { }` block
collects all field errors and then either raises `IncorrectInput` or returns the
validated object. From that point on, normal `Raise` semantics resume — short-circuit on
the first logical failure, as usual. The two modes coexist cleanly because they operate
at different layers of the call stack.

See the
[Arrow guide on working with typed errors](https://arrow-kt.io/learn/typed-errors/working-with-typed-errors/)
for the general `Raise` DSL primitives (`ensure`, `raise`, `withError`, `recover`) that
are used alongside this pattern.

---

## Layer summary

| Layer | API | Error type |
|---|---|---|
| Individual rule | `ensureOrAccumulate` in `RaiseAccumulate<String>` | `String` |
| Field validator | `accumulate { }` + `withError(::InvalidXxx)` | `InvalidField` |
| Object validator | `accumulate { val x by accumulating { } }` + `withError(::IncorrectInput)` | `IncorrectInput` |
| Service / route | normal `Raise<IncorrectInput>` | `IncorrectInput` |

Each layer translates errors upward using `withError`, and the `NonEmptyList` wrapper
ensures the "at least one error" invariant is preserved across every translation.
