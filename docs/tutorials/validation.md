# Validation with typed errors

"Validation" is where fail-fast error handling is not what we want, but instead need to _accumulate_ or collect errors.
This is useful for several scenarios, such as validating a form with multiple fields, where sending only the first error
is not very helpful but also when executing several independent tasks without short-circuiting each other but evaluate
all of them and inspect the results.

That is exactly what Arrow's `accumulate` `Raise` DSL gives us. In this tutorial we'll walk through `Validation.kt`,
from the smallest string rule up to full request validation.

Note: `accumulate` and `RaiseAccumulate` used in this post are still experimental in Arrow so the code uses
`@OptIn(ExperimentalRaiseAccumulateApi::class)`.

---

## Modeling multiple errors (title in progress)

We _accumulate_ errors into `NonEmptyList` during validation. This ensures that whenever we receive an error value,
there is _at least one_ error value; otherwise we expect a success value. Instead of working with "raw"
`NonEmptyList<String` type we create a specific `InvalidPassword` type, this gives our type a more meaningful meaning
and distinct it from other `NonEmptyList<String` errors similar to have we avoid _primitive obsession_.

<!--- INCLUDE
import arrow.core.NonEmptyList
-->

```kotlin
data class InvalidPassword(val errors: NonEmptyList<String>)
```

Now that we have our _failure_ or _error_ defined, we should also define our _success_ value. To avoid defensively
having to revalidate, we create a new type that represents our validated value. In this case `Password`. Since passwords
have other constraints like _holding sensitive information_, we can also encode that into our type.

```kotlin
@JvmInline
value class Password(private val value: String) {
    fun raw(): String = value
    override fun toString(): String = "Password(*****)"
}
```

<!--- KNIT example-validation-01.kt -->

Our `Password` type does a couple of things:

- It avoids primitive obsession by giving us a dedicated type instead of `String`
- Forces us to use `raw()` value to be more explicit about when the _raw sensitive_ `value` is used.
- Ensures the sensitive value is never printed when (data) classes holding a `Password` get logged (`toString`).

What is still missing is our validation, but we want to guarantee that no-one can create an incorrect `Password`. This
can be achieved by making the `constructor` `private` and providing a synthetic constructor, but remember that a
`private constructor` can only be accessed from the `companion object`. To do so, we can either create a named
constructor like `Password.create` or use the `invoke` `operator` and _emulate_ the constructor.

<!--- INCLUDE
import arrow.core.NonEmptyList
import arrow.core.raise.context.Raise

data class InvalidPassword(val errors: NonEmptyList<String>)
-->

```kotlin
@JvmInline
value class Password private constructor(private val value: String) {
    fun raw(): String = value
    override fun toString(): String = "Password(*****)"

    companion object {
        context(_: Raise<InvalidPassword>)
        fun create(value: String): Password = TODO()

        context(_: Raise<InvalidPassword>)
        operator fun invoke(value: String): Password = create(value)
    }
}
```

<!--- KNIT example-validation-02.kt -->

Now for our actual validation to validate a raw `String` into a validated `Password` with the following rules:

- It must be
    - Not blank
    - Minimum 8 in length
    - Maximum 8 in length
- It must contain:
    - At least one uppercase letter
    - At least one lowercase letter
    - At least one number
    - At least one special character

In the `create` or `invoke` function when any of the validation _rules_ is violated, we want to _accumulate_ the errors
into `InvalidPassword`'s `NonEmptyList<String>`. We represent an individual validation violation as a simple `String`
message, but we could have used any other more complex domain like `data class ValidationError(...)` or
`sealed interface ValidationError`.

```kotlin
context(_: Accumulate<String>)
fun String.ensureNotBlank() {
    ensureOrAccumulate(isNotBlank()) { "Password cannot be blank" }
}
```

<!--- KNIT example-validation-03.kt -->

To _raise_ errors of type `String` we use `Raise<String>`, but to _accumulate_ errors of type `String` we need
`Accumulate<String>` so individual _condition_ can be defined using `context(_: Accumulate<String>)`. The `Accumulate`
DSL offers functions like `ensureOrAccumulate(condition) { "message" }`, `ensureNotNull(value) { "message" }`, `mapOrAccumulate {  

To ensure the operation doesn't short-circuit but continues while _accumulating_ errors, we use
`ensureOrAccumulate(condition) { "message" }` instead of `ensure(condition) { "message" }`.

An individual error is of type `String`, a simple error message, so `Raise<String>` is how we _raise_ a violation. So we
need to get a `Raise<String>` from our `Raise<InvalidPassword>` such that an error _raised_ in `Raise<String>` gets
accumulated into `Raise<InvalidPassword>`. We can achieve this by combining `withError`, and `accumulate`. Let’s quickly
review both.

<!--- INCLUDE
import arrow.core.NonEmptyList
import arrow.core.raise.context.Raise
import arrow.core.raise.context.RaiseAccumulate
import arrow.core.raise.context.ensureOrAccumulate
import arrow.core.raise.context.raise
import arrow.core.raise.recover
-->

```kotlin
context(raise: Raise<Error>)
inline fun <Error, OtherError, A> withError(
    transform: (OtherError) -> Error,
    block: context(Raise<OtherError>) () -> A
): A = recover(block) { raise(transform(it)) }
```

`withError` creates a `block: Raise<NonEmptyList<String>>.() -> A` scope `block` for our `Raise<InvalidPassword>` scope
given a
`transform: (NonEmptyList<String>) -> InvalidPassword` which is the `InvalidPassword` constructor. This is useful so
that we can express

```kotlin
context(raise: Raise<NonEmptyList<Error>>)
inline fun <Error, A> accumulate(block: context(RaiseAccumulate<Error>) () -> A): A = TODO()
```

```kotlin
context(_: RaiseAccumulate<String>)
fun String.validate() {
    ensureOrAccumulate(isNotBlank()) { "Password cannot be blank" }
    ensureOrAccumulate(length >= 8) { "Password must be minimum 8 characters long" }
    ensureOrAccumulate(length <= 100) { "Password must be maximum 100 characters long" }
    ensureOrAccumulate(contains("[A-Z]".toRegex())) { "At least one uppercase letter" }
    ensureOrAccumulate(contains("[a-z]".toRegex())) { "At least one lowercase letter" }
    ensureOrAccumulate(contains("[0-9]".toRegex())) { "At least one number" }
    ensureOrAccumulate(contains("""[$#%&^*!?{}\[\]+=<€>±§|]""".toRegex())) { "At least one special character" }
}
```

<!--- KNIT example-validation-04.kt -->

A lot is going on here, so lets unpack it.

Again, `NonEmptyList` tells us something useful. If `IncorrectInput` exists, at least one field failed.

So the structure is:

```text
IncorrectInput
  -> NonEmptyList<InvalidField>
       -> NonEmptyList<String>
```

This mirrors how we want validation to behave. Multiple fields can fail, and each field can have multiple reasons.

---

## Leaf rules: `RaiseAccumulate` and `ensureOrAccumulate`

At the bottom we have small predicates on `String`. They run in a
`RaiseAccumulate<String>` context:

<!--- INCLUDE 
import arrow.core.raise.context.RaiseAccumulate
import arrow.core.raise.context.ensureOrAccumulate
-->

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

private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@(.+)$")

@IgnorableReturnValue
context(_: RaiseAccumulate<String>)
private fun String.looksLikeEmail(): String = also {
    val _ = ensureOrAccumulate(emailPattern.matches(this)) { "'$this' is invalid email" }
}
```

<!--- KNIT example-validation-05.kt -->

`ensureOrAccumulate` is the accumulating version of `ensure`.

With normal `ensure`, a failed condition short-circuits immediately. With
`ensureOrAccumulate`, the error is recorded and validation continues. This lets us run all rules for a field and return
all messages at once.

The functions return the original `String` so they compose nicely, but callers don't need the return value. That is why
the code uses `@IgnorableReturnValue`.

---

## Field validation: collect rules, then name the field

Individual rules are grouped into field validators with `accumulate`:

<!--- INCLUDE
import arrow.core.NonEmptyList
import arrow.core.raise.context.Raise
import arrow.core.raise.context.RaiseAccumulate
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.ensureOrAccumulate

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
-->

```kotlin
context(_: Raise<NonEmptyList<String>>)
private fun String.passwordRules(): String = accumulate {
    notBlank()
    minSize(8)
    maxSize(100)
}
```

Inside the block we are in a `RaiseAccumulate<String>` context, so we can call
`notBlank`, `minSize`, and `maxSize` directly.

If no rule fails, the block returns the password. If one or more rules fail, Arrow raises a `NonEmptyList<String>`
containing all messages.

Now we still need to say *which* field failed. That is what `withError` is for:

<!--- INCLUDE
import arrow.core.NonEmptyList
import arrow.core.raise.context.Raise
import arrow.core.raise.context.withError
import arrow.core.raise.context.RaiseAccumulate
import arrow.core.raise.context.accumulate
import arrow.core.raise.context.ensureOrAccumulate

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

context(_: Raise<NonEmptyList<String>>)
private fun String.passwordRules(): String = accumulate {
    notBlank()
    minSize(8)
    maxSize(100)
}

sealed interface InvalidField {
    val errors: NonEmptyList<String>
    val field: String
}

data class InvalidPassword(override val errors: NonEmptyList<String>) : InvalidField {
    override val field: String = "password"
}
-->

```kotlin
context(_: Raise<InvalidField>)
private fun String.passwordValidation(): String =
    withError(::InvalidPassword) { passwordRules() }
```

`passwordRules()` raises `NonEmptyList<String>`. `withError(::InvalidPassword)` turns that into `InvalidPassword`, which
implements `InvalidField`.

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

`accumulate { ... }` opens a scope that can collect multiple `InvalidField` values. Inside it, `by accumulating { ... }`
runs one field validator. If the field raises, the error is stored and the next field still runs.

The `by` is property delegation. Arrow needs this because there may not be a valid
`username`, `email`, or `password` value yet. The delegate lets Arrow delay reading the value until it knows all fields
succeeded.

If all fields are valid, the last line builds a normal `RegisterUser`. If any field failed, the collected
`NonEmptyList<InvalidField>` is raised instead.

The outer `withError(::IncorrectInput)` performs the final translation:

```text
NonEmptyList<InvalidField> -> IncorrectInput
```

So callers only need to know about `IncorrectInput`, while the internals still preserve all field-level information.

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

This shape is fine for many domain operations, but it is not great for input validation. If `username` fails, we never
check `email` or `password`. The API consumer fixes one error, sends the form again, and only then discovers the next
error.

With `accumulate` and `by accumulating`, all three fields are checked in one pass. That is the behavior we usually want
at the edge of an HTTP API.

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

The `?.` is doing exactly what we want. Missing values remain `null` and do not contribute errors. Present values are
validated, and invalid present values are added to the accumulator.

After validation, `UserService.update` performs one more domain check with normal fail-fast `Raise`: at least one field
must be present.

---

## Collections: `mapOrAccumulate`

Article tags are validated as a collection:

```kotlin
context(_: Raise<InvalidField>)
private fun List<String>.validTags(): Set<String> =
    withError(::InvalidTag) { mapOrAccumulate { it.trim().notBlank() }.toSet() }
```

`mapOrAccumulate` is the accumulating counterpart of `map`. It runs the lambda for every element, collects any raised
`String` errors, and raises them together as a
`NonEmptyList<String>` if anything failed.

Then `withError(::InvalidTag)` wraps those messages into one `InvalidTag`.

This means `tags` is still treated as one field from the API's perspective, even if multiple elements inside the list
were invalid. That keeps the response simple without losing the useful messages.

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

`FeedOffset` and `FeedLimit` are `@JvmInline value class` wrappers. Once we have one, we know that the raw `Int` passed
validation.

<!--- INCLUDE
import arrow.core.NonEmptyList
-->

```kotlin
sealed interface InvalidField {
    val errors: NonEmptyList<String>
    val field: String
}

data class IncorrectInput(val errors: NonEmptyList<InvalidField>)
```

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

This is a small pattern, but I like it a lot. The route layer receives strings and numbers from HTTP, and the service
layer receives values that already encode their invariants.

---

## Back to the normal `Raise` DSL

Every public `validate()` function raises `IncorrectInput`. That means route handlers and services can call validation
like any other typed-error function:

```kotlin
context(_: DomainErrors)
fun register(input: RegisterUser): JwtToken {
    val (username, email, password) = input.validate()
    val userId = repo.insert(username, email, password)
    return jwtService.generateJwtToken(userId)
}
```

There is no impedance mismatch between accumulating validation and the rest of the fail-fast service layer.

The key is that accumulation is scoped. Inside `accumulate { ... }`, we collect as many validation errors as possible.
Once `validate()` returns, we have a plain value. From that point on, normal `Raise` semantics resume and the next
logical error short-circuits as usual.

So we get the best of both modes:

| Layer            | API                                                                        | Error type                        |
|------------------|----------------------------------------------------------------------------|-----------------------------------|
| Individual rule  | `ensureOrAccumulate` in `RaiseAccumulate<String>`                          | `String`                          |
| Field validator  | `accumulate { }` + `withError(::InvalidXxx)`                               | `InvalidField`                    |
| Object validator | `accumulate { val x by accumulating { } }` + `withError(::IncorrectInput)` | `IncorrectInput`                  |
| Service / route  | normal `Raise`                                                             | `IncorrectInput` or `DomainError` |

---

## Where to go next

Validation gives us a precise `IncorrectInput` value with all field errors preserved.
The [end-to-end walkthrough](end-to-end-feature.md) shows where that error goes next:
through the service layer, into `ErrorRoutes.kt`, and finally into the RealWorld
`GenericErrorModel` response.

Thank you for reading! I hope this makes `accumulate` feel like a small extension of the
`Raise` DSL rather than a separate validation framework.
