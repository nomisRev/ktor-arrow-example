# Routes, validation, and model boundaries

This project defines its HTTP contracts with [Spine](https://gitlab.com/opensavvy/spine) and models expected
failures as Arrow `DomainError`s. Keep the HTTP/wire boundary separate from the business layer:

```
JSON -> @Serializable wire DTO -> validate/map -> business/service input -> service -> persistence
```

## Define the contract in `Api.kt`

Every endpoint is declared once, as data, in `Api.kt`. Route handlers implement an already-declared `Endpoint`; they
do not choose paths or status codes.

- The root object extends `opensavvy.spine.api.RootResource` (aliased as `SpineRootResource`).
- Use `StaticResource<Parent>("segment", Parent)` for fixed path segments and
  `DynamicResource<Parent>("name", Parent)` for path parameters.
- Declare request and response types with `.request<Body>()` and `.response<Body>()`.
- Domain failures use `.failure<GenericErrorModel>(HttpStatusCode.UnprocessableEntity)`.

Every endpoint uses `GenericErrorModel` at `422 Unprocessable Entity`; do not invent a per-endpoint error payload
according to the [Conduit Spec](/api/openapi.yml). Example:

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

## Route handlers and the wire boundary

Route files contain the HTTP adaptation, not business or persistence logic. A request model decoded from JSON is a
wire model: it is normally `@Serializable`, uses primitive values (`String`, `Int`, nullable fields), and mirrors the
HTTP payload. It must not be passed directly to a service.

When a request needs validation or route context, put a conversion function on the wire DTO. The conversion should:

1. validate and normalize all supplied fields;
2. accumulate errors for independent fields with `accumulate`/`accumulating`;
3. raise one `IncorrectInput` when validation fails; and
4. construct a non-serializable business input using validated value classes and domain identifiers.

Example:

```kotlin
@Serializable
data class NewUser(val username: String, val email: String, val password: String) {
    context(_: Raise<IncorrectInput>)
    fun toRegisterUser() = withError(::IncorrectInput) {
        accumulate {
            val username by accumulating { Username(username) }
            val email by accumulating { Email(email) }
            val password by accumulating { Password(password) }
            RegisterUser(username, email, password)
        }
    }
}
```

Typical route code is therefore:

```kotlin
route(Users.register) {
    val register = body.user.toRegisterUser() // Raise<IncorrectInput>
    val token = userService.register(register)
    respond(UserWrapper(register.toUser(token)), HttpStatusCode.Created)
}
```

The service input is an ordinary business model, not a wire DTO. Its types express invariants: for example,
`RegisterUser` contains `Username`, `Email`, and `Password`, whose constructors are private and can only be reached
through their validating factory functions. Services and persistence should accept these business models and should
not repeat HTTP validation. Unwrap value classes only at the persistence/SQL boundary (`.value` or `.raw()`).
Note: `Password` also hides the sensitive information by overriding `toString()`.  Example:

```kotlin
data class RegisterUser(val username: Username, val email: Email, val password: Password)

@JvmInline
value class Username private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidUsername>)
        operator fun invoke(value: String): Username = withError(::InvalidUsername) {
            val normalized = value.trim()
            accumulate {
                normalized.notBlank()
                normalized.minSize(MIN_USERNAME_LENGTH)
                normalized.maxSize(MAX_USERNAME_LENGTH)
                Username(normalized)
            }
        }
    }
}

@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        context(_: Raise<InvalidEmail>)
        operator fun invoke(value: String): Email = withError(::InvalidEmail) {
            val normalized = value.trim()
            accumulate {
                normalized.notBlank()
                normalized.maxSize(MAX_EMAIL_LENGTH)
                normalized.looksLikeEmail()
                Email(normalized)
            }
        }
    }
}

@JvmInline
value class Password private constructor(private val value: String) {
    fun raw(): String = value
    override fun toString(): String = "Password(*****)"

    companion object {
        context(_: Raise<InvalidPassword>)
        operator fun invoke(value: String): Password = withError(::InvalidPassword) {
            accumulate {
                value.notBlank()
                value.minSize(MIN_PASSWORD_LENGTH)
                value.maxSize(MAX_PASSWORD_LENGTH)
                ensureOrAccumulate(value.contains(uppercase)) { "At least one uppercase letter" }
                ensureOrAccumulate(value.contains(lowercase)) { "At least one lowercase letter" }
                ensureOrAccumulate(value.contains(number)) { "At least one number" }
                ensureOrAccumulate(value.contains(special)) { "At least one special character" }
                Password(value)
            }
        }
    }
}
```

`opensavvy.spine.api.Parameters` classes follow the same boundary. `parameters.validate(...)` converts `ArticlesParameters` or
`FeedParameters` into `GetArticles`/`GetFeed` before calling the service. Path values and authenticated user IDs are
also converted into domain identifiers at this boundary.

Route handlers call services directly and never use `try/catch` for expected failures. `route(endpoint) { ... }`
provides `context(DomainErrors)`; `raise`, `ensure`, `ensureNotNull`, and Arrow `catch` are the expected-failure
vocabulary.

## `ErrorRoutes.kt`: mapping failures to HTTP

`Route.route(endpoint) { block }` runs the block inside `arrow.core.raise.recover` and maps the resulting
`DomainError` through `DomainError.toGenericErrorModel()`:

```kotlin
inline fun <...> Route.route(
    endpoint: Endpoint<In, Out, Failure, Params>,
    crossinline block: suspend context(DomainErrors) TypedResponseScope<...>.() -> Unit,
): Unit =
    route(endpoint) response@{
        recover(
            block = { block() },
            recover = { error: DomainError -> fail(error.toGenericErrorModel()) },
        )
    }
```

This is the only place that turns a domain failure into an HTTP error response. When adding a `DomainError` subtype,
add its exhaustive branch to `toGenericErrorModel`.

## Error hierarchy and `Raise` scope

`DomainError` is the top-level sealed interface. Feature-specific errors extend it:

```kotlin
sealed interface DomainError
sealed interface ValidationError : DomainError
data class IncorrectInput(val errors: NonEmptyList<InvalidField>) : ValidationError
sealed interface UserError : DomainError
data class UserNotFound(val property: String) : UserError
sealed interface ArticleError : DomainError
```

Declare the narrowest `Raise` context a function needs. Persistence functions should raise only their feature error
(or a single specific error). A service uses `DomainErrors` when it combines validation, persistence, JWT, or other
error families. Route blocks use `DomainErrors` because they are the HTTP error boundary.

## Validation in `Validation.kt`

Validation must report every invalid field and every broken rule for that field. Use Arrow's experimental
`accumulate` API rather than manually building `NonEmptyList`s.

### Field validation

A field validator raises one `InvalidField` and accumulates its rule messages with `RaiseAccumulate<String>`:

```kotlin
context(_: Raise<InvalidEmail>)
operator fun Email.Companion.invoke(value: String): Email =
    withError(::InvalidEmail) {
        val normalized = value.trim()
        accumulate {
            normalized.notBlank()
            normalized.maxSize(MAX_EMAIL_LENGTH)
            normalized.looksLikeEmail()
            Email(normalized)
        }
    }
```

In this project `Email`, `Username`, and `Password` are `@JvmInline value class`es with private constructors.
Their validating factories normalize where appropriate and are the only way to create those business values. Reuse
small `RaiseAccumulate<String>` rules (`notBlank`, `minSize`, `maxSize`, `looksLikeEmail`) instead of duplicating
rule logic.

For ordinary text fields, use a field-specific validator such as `validTitle`, `validDescription`, or `validBody`.
Lists use `mapOrAccumulate`; optional fields use `field?.let { ... }` inside an accumulating block so `null` does not
raise a validation error.

### DTO-to-business validation

Each DTO conversion validates fields independently and reconstructs a business model from the validated results:

```kotlin
context(_: Raise<IncorrectInput>)
fun NewUser.toRegisterUser(): RegisterUser =
    withError(::IncorrectInput) {
        accumulate {
            val username by accumulating { Username(username) }
            val email by accumulating { Email(email) }
            val password by accumulating { Password(password) }
            RegisterUser(username, email, password)
        }
    }
```

Use the same shape for updates and other requests. Keep HTTP names and serialization annotations on the wire DTO;
keep invariants, value classes, and business-specific input composition on the business model. A conversion may also
include trusted route context, such as `userId` or `slug`, but it must not move business rules that belong in the
service.

- Define one `InvalidField` subtype per validated field, carrying `NonEmptyList<String>` and a fixed field name.
- Wrap the accumulated fields in `IncorrectInput`.
- Preserve normalized values in the returned business model.
- Keep business rules such as “an update must change at least one field” in the service (`Update`), not in the wire
  DTO conversion.
