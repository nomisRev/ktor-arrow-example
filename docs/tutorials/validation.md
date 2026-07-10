# Validation with typed errors

Validation is where fail-fast error handling is often not what we want. If a user sends
a form with three invalid fields, returning only the first error is not very helpful.
We'd like to validate everything we can, collect all failures, and still end up with a
plain Kotlin value when validation succeeds.

That is exactly what Arrow's `accumulate` API gives us.

In this tutorial we'll walk through `Validation.kt`, from the smallest string rule up
to full request validation.

Note: the file uses `@OptIn(ExperimentalRaiseAccumulateApi::class)` because
`accumulate` and `RaiseAccumulate` are still experimental in Arrow. The API is already
very usable, but Kotlin asks us to acknowledge that opt-in for now.

---

## The error model

Let's start with the types. Every field error implements `InvalidField`:

```kotlin
sealed interface InvalidField {
    val errors: NonEmptyList<String>
    val field: String
}

data class InvalidEmail(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "email"
}

data class InvalidPassword(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "password"
}

// ... InvalidUsername, InvalidTitle, InvalidBody, etc.
```

The important type here is `NonEmptyList<String>`.

If a value is an `InvalidField`, then we know it has *at least one* error message. We
cannot accidentally construct an invalid field with an empty list of reasons. That small
invariant makes the rest of the validation code much nicer.

At the top of the validation hierarchy we have `IncorrectInput`:

```kotlin
data class IncorrectInput(val errors: NonEmptyList<InvalidField>) : ValidationError
```

Again, `NonEmptyList` tells us something useful. If `IncorrectInput` exists, at least
one field failed.

So the structure is:

```text
IncorrectInput
  -> NonEmptyList<InvalidField>
       -> NonEmptyList<String>
```

This mirrors how we want validation to behave. Multiple fields can fail, and each field
can have multiple reasons.

---

## Leaf rules: `RaiseAccumulate` and `ensureOrAccumulate`

At the bottom we have small predicates on `String`. They run in a
`RaiseAccumulate<String>` context:

```kotlin
@IgnorableReturnValue
context(_: RaiseAccumulate<String>)
private fun String.notBlank(): String = also {
    val _ = ensureOrAccumulate(isNotBlank()) { "Cannot be blank" }
}

@IgnorableReturnValue
context(_: RaiseAccumulate<String>)
private fun String.minSize(size: Int): String = also {
    val _ = ensureOrAccumulate(length >= size) { "is too short (minimum is $size characters)" }
}

@IgnorableReturnValue
context(_: RaiseAccumulate<String>)
private fun String.maxSize(size: Int): String = also {
    val _ = ensureOrAccumulate(length <= size) { "is too long (maximum is $size characters)" }
}

@IgnorableReturnValue
context(_: RaiseAccumulate<String>)
private fun String.looksLikeEmail(): String = also {
    val _ = ensureOrAccumulate(emailPattern.matches(this)) { "'$this' is invalid email" }
}
```

`ensureOrAccumulate` is the accumulating version of `ensure`.

With normal `ensure`, a failed condition short-circuits immediately. With
`ensureOrAccumulate`, the error is recorded and validation continues. This lets us run
all rules for a field and return all messages at once.

The functions return the original `String` so they compose nicely, but callers don't
need the return value. That is why the code uses `@IgnorableReturnValue`.

---

## Field validation: collect rules, then name the field

Individual rules are grouped into field validators with `accumulate`:

```kotlin
context(_: Raise<NonEmptyList<String>>)
private fun String.passwordRules(): String = accumulate {
    notBlank()
    minSize(MIN_PASSWORD_LENGTH)
    maxSize(MAX_PASSWORD_LENGTH)
    this@passwordRules
}
```

Inside the block we are in a `RaiseAccumulate<String>` context, so we can call
`notBlank`, `minSize`, and `maxSize` directly.

If no rule fails, the block returns the password. If one or more rules fail, Arrow
raises a `NonEmptyList<String>` containing all messages.

Now we still need to say *which* field failed. That is what `withError` is for:

```kotlin
context(_: Raise<InvalidField>)
private fun String.passwordValidation(): String =
    withError(::InvalidPassword) { passwordRules() }
```

`passwordRules()` raises `NonEmptyList<String>`. `withError(::InvalidPassword)` turns
that into `InvalidPassword`, which implements `InvalidField`.

The public field validator exposes the common supertype:

```kotlin
context(_: Raise<InvalidField>)
private fun String.validPassword(): String = passwordValidation()
```

Email and username validation follow the same shape:

```kotlin
context(_: Raise<InvalidField>)
private fun String.emailValidation(): String =
    withError(::InvalidEmail) { trim().emailRules() }

context(_: Raise<InvalidField>)
private fun String.usernameValidation(): String =
    withError(::InvalidUsername) { trim().usernameRules() }
```

This is the first important translation:

```text
String rule errors -> NonEmptyList<String> -> InvalidField
```

---

## Object validation: `accumulate` and `by accumulating`

Once every field can raise `InvalidField`, we can validate a whole object.

Here is `RegisterUser`:

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

There are a couple of pieces here.

`accumulate { ... }` opens a scope that can collect multiple `InvalidField` values.
Inside it, `by accumulating { ... }` runs one field validator. If the field raises, the
error is stored and the next field still runs.

The `by` is property delegation. Arrow needs this because there may not be a valid
`username`, `email`, or `password` value yet. The delegate lets Arrow delay reading the
value until it knows all fields succeeded.

If all fields are valid, the last line builds a normal `RegisterUser`. If any field
failed, the collected `NonEmptyList<InvalidField>` is raised instead.

The outer `withError(::IncorrectInput)` performs the final translation:

```text
NonEmptyList<InvalidField> -> IncorrectInput
```

So callers only need to know about `IncorrectInput`, while the internals still preserve
all field-level information.

---

## Why not validate sequentially?

Let's compare it with a fail-fast implementation:

```kotlin
// Short-circuits at the first failure.
context(_: Raise<IncorrectInput>)
fun RegisterUser.validateNaive(): RegisterUser {
    val username = username.validUsername()
    val email = email.validEmail()
    val password = password.validPassword()
    return RegisterUser(username, email, password)
}
```

This shape is fine for many domain operations, but it is not great for input
validation. If `username` fails, we never check `email` or `password`. The API consumer
fixes one error, sends the form again, and only then discovers the next error.

With `accumulate` and `by accumulating`, all three fields are checked in one pass.
That is the behavior we usually want at the edge of an HTTP API.

> See the [Arrow validation docs](https://arrow-kt.io/learn/typed-errors/validation/#fail-first-vs-accumulation)
> for a side-by-side comparison of fail-first and accumulating validation.

---

## Nullable fields: optional values stay optional

`Update` represents a partial profile update, so every user-editable field is nullable:

```kotlin
context(_: Raise<IncorrectInput>)
fun Update.validate(): Update =
    withError(::IncorrectInput) {
        accumulate {
            val username by accumulating { username?.validUsername() }
            val email by accumulating { email?.validEmail() }
            val password by accumulating { password?.validPassword() }
            Update(userId, username, email, password, bio, image)
        }
    }
```

The `?.` is doing exactly what we want. Missing values remain `null` and do not
contribute errors. Present values are validated, and invalid present values are added to
the accumulator.

After validation, `UserService.update` performs one more domain check with normal
fail-fast `Raise`: at least one field must be present.

---

## Collections: `mapOrAccumulate`

Article tags are validated as a collection:

```kotlin
context(_: Raise<InvalidField>)
private fun List<String>.validTags(): Set<String> =
    withError(::InvalidTag) { mapOrAccumulate { it.trim().notBlank() }.toSet() }
```

`mapOrAccumulate` is the accumulating counterpart of `map`. It runs the lambda for every
element, collects any raised `String` errors, and raises them together as a
`NonEmptyList<String>` if anything failed.

Then `withError(::InvalidTag)` wraps those messages into one `InvalidTag`.

This means `tags` is still treated as one field from the API's perspective, even if
multiple elements inside the list were invalid. That keeps the response simple without
losing the useful messages.

---

## Value classes: validated query parameters

We also validate query parameters into value classes before passing them to services:

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

`FeedOffset` and `FeedLimit` are `@JvmInline value class` wrappers. Once we have one,
we know that the raw `Int` passed validation.

The full feed parameters then accumulate both values:

```kotlin
context(_: Raise<IncorrectInput>)
fun FeedParameters.validate(userId: UserId): GetFeed =
    withError(::IncorrectInput) {
        accumulate {
            val offset by accumulating { offset.validFeedOffset() }
            val limit by accumulating { limit.validFeedLimit() }
            GetFeed(userId, limit.limit, offset.offset)
        }
    }
```

This is a small pattern, but I like it a lot. The route layer receives strings and
numbers from HTTP, and the service layer receives values that already encode their
invariants.

---

## Back to the normal `Raise` DSL

Every public `validate()` function raises `IncorrectInput`. That means route handlers
and services can call validation like any other typed-error function:

```kotlin
context(_: DomainErrors)
fun register(input: RegisterUser): JwtToken {
    val (username, email, password) = input.validate()
    val userId = repo.insert(username, email, password)
    return jwtService.generateJwtToken(userId)
}
```

There is no impedance mismatch between accumulating validation and the rest of the
fail-fast service layer.

The key is that accumulation is scoped. Inside `accumulate { ... }`, we collect as many
validation errors as possible. Once `validate()` returns, we have a plain value. From
that point on, normal `Raise` semantics resume and the next logical error
short-circuits as usual.

So we get the best of both modes:

| Layer | API | Error type |
|---|---|---|
| Individual rule | `ensureOrAccumulate` in `RaiseAccumulate<String>` | `String` |
| Field validator | `accumulate { }` + `withError(::InvalidXxx)` | `InvalidField` |
| Object validator | `accumulate { val x by accumulating { } }` + `withError(::IncorrectInput)` | `IncorrectInput` |
| Service / route | normal `Raise` | `IncorrectInput` or `DomainError` |

---

## Where to go next

Validation gives us a precise `IncorrectInput` value with all field errors preserved.
The [end-to-end walkthrough](end-to-end-feature.md) shows where that error goes next:
through the service layer, into `ErrorRoutes.kt`, and finally into the RealWorld
`GenericErrorModel` response.

Thank you for reading! I hope this makes `accumulate` feel like a small extension of the
`Raise` DSL rather than a separate validation framework.
