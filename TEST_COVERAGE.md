# Test Coverage Improvement Tasks

This document turns the Kover coverage review into test work. The current aggregate line coverage is distorted by SQLDelight-generated code; the priority is to cover observable handwritten application behavior before pursuing generated-code percentages.

## 1. Domain-error to HTTP-error mapping

**Priority: high**

Add route-level tests that assert both the HTTP status and the serialized `GenericErrorModel` for every domain error currently not exercised. These tests protect the API's error contract and execute `DomainError.toGenericErrorModel` in [`src/main/kotlin/io/github/nomisrev/DomainError.kt`](src/main/kotlin/io/github/nomisrev/DomainError.kt).

- [ ] Add a registration test that attempts to reuse an existing email address.
  - Create a user, then register a second user with the same email.
  - Assert `422 Unprocessable Entity`.
  - Assert the response error is `"<email> is already registered"`.
  - Covers `EmailAlreadyExists`.

- [ ] Add a registration test that attempts to reuse an existing username.
  - Create a user, then register a second user with the same username.
  - Assert `422 Unprocessable Entity`.
  - Assert the response error is `"Username <username> already exists"`.
  - Covers `UsernameAlreadyExists`.

- [ ] Add a login test with a valid email and an incorrect password.
  - Register a user and submit a valid-format but incorrect password to the login endpoint.
  - Assert `422 Unprocessable Entity`.
  - Assert the response error is `"Password not matched"`.
  - Covers `PasswordNotMatched`.

- [ ] Add a malformed request-body test for a required JSON field.
  - Submit JSON that omits a required request field rather than a semantically invalid field.
  - Assert the expected failure status and `"Json is missing fields: ..."` response body.
  - Covers `IncorrectJson`.

- [ ] Add an article update test with an empty update payload.
  - Create an article, submit an authenticated update containing no mutable fields, and assert the API contract.
  - If the endpoint should reject it, assert `422 Unprocessable Entity` and the `EmptyUpdate` message; otherwise, document and test the intended no-op behavior.
  - Covers or resolves the currently unexercised `EmptyUpdate` path.

- [ ] Add a comment-delete test with a non-numeric comment identifier.
  - Invoke the delete endpoint with an authenticated user and a non-numeric ID.
  - Assert `422 Unprocessable Entity` and `"Missing commentId must be a number parameter in request"`.
  - Covers `MissingParameter`.

- [ ] Add an article-create test that forces slug generation failure, if the behavior is reachable through the public API.
  - Prefer a focused `ArticleService` test with a fake `SlugGenerator`/persistence boundary if creating collisions through HTTP is impractical.
  - Assert the `CannotGenerateSlug` response mapping or raised error.
  - Covers `CannotGenerateSlug`.

## 2. Authorization failures for article and comment ownership

**Priority: high**

Extend [`src/test/kotlin/io/github/nomisrev/articles/ArticlesRouteSpec.kt`](src/test/kotlin/io/github/nomisrev/articles/ArticlesRouteSpec.kt) with ownership tests. Each test should use distinct author and attacker users, perform the request as the attacker, and verify that persisted data is unchanged after the rejected request.

- [ ] Reject an article update by a non-author.
  - Create an article as user A and submit `PUT`/update as user B.
  - Assert `422 Unprocessable Entity`.
  - Assert `"User is not the author of the article"`.
  - Fetch the article and assert its title, description, and body are unchanged.
  - Covers `NotArticleAuthor` for updates.

- [ ] Reject article deletion by a non-author.
  - Create an article as user A and submit delete as user B.
  - Assert `422 Unprocessable Entity` and the author error response.
  - Fetch the article and assert it still exists.
  - Covers `NotArticleAuthor` for deletes.

- [ ] Reject deletion of another user's comment.
  - Create an article and a comment as user A; delete the comment as user B.
  - Assert `422 Unprocessable Entity`.
  - Assert `"User is not the author of the comment"`.
  - List comments and assert the original comment remains.
  - Covers `NotCommentAuthor`.

- [ ] Reject deletion of a nonexistent comment.
  - Delete a numeric comment ID that does not exist as an authenticated user.
  - Assert `422 Unprocessable Entity`.
  - Assert `"Comment with ID <id> not found"`.
  - Covers `CommentNotFound`.

- [ ] Verify invalid or unauthenticated requests cannot mutate articles, favorites, or comments.
  - Existing tests cover selected authentication cases; add a compact matrix only for mutation endpoints not already covered.
  - For every rejected request, read the resource afterwards and assert no state change.

## 3. User failure and update scenarios

**Priority: high**

Expand [`src/test/kotlin/io/github/nomisrev/users/UserRouteSpec.kt`](src/test/kotlin/io/github/nomisrev/users/UserRouteSpec.kt) with the following API-level cases.

- [ ] Reject login for an email that has no corresponding user.
  - Use valid-format credentials so validation does not mask the service error.
  - Assert the expected status and `UserNotFound` response message.

- [ ] Reject login with a wrong password.
  - Assert the status and `PasswordNotMatched` response message.

- [ ] Require authentication for current-user read.
  - Call `GET /user` without a bearer token.
  - Assert `401 Unauthorized`.

- [ ] Require authentication for current-user update.
  - Call `PUT /user` without a bearer token.
  - Assert `401 Unauthorized` and verify no user data changed.

- [ ] Reject an update that would duplicate another user's email.
  - Create two users and update user B to user A's email.
  - Assert `422 Unprocessable Entity` and the duplicate-email response.

- [ ] Reject an update that would duplicate another user's username.
  - Create two users and update user B to user A's username.
  - Assert `422 Unprocessable Entity` and the duplicate-username response.

- [ ] Cover updates to password, bio, and image.
  - Update all three fields, assert they are returned by `GET /user`, and log in with the new password.
  - Ensure the old password no longer authenticates if that is the intended behavior.

- [ ] Define and test an empty update payload.
  - Submit `UpdateUser()` with no fields.
  - Assert the intended result: either a validation error (`EmptyUpdate`) or an explicitly supported no-op.

- [ ] Validate that failed updates are atomic.
  - Submit a request containing one valid field and one invalid or conflicting field.
  - Assert the request fails and the valid field was not persisted.

## 4. JWT invalid-credential behavior

**Priority: high for authentication boundaries; lower for signing-library failures**

Expand [`src/test/kotlin/io/github/nomisrev/auth/JwtServiceSpec.kt`](src/test/kotlin/io/github/nomisrev/auth/JwtServiceSpec.kt). Test through an authenticated endpoint such as `GET /user` so the Ktor authentication integration is also exercised.

- [ ] Reject a malformed bearer token.
  - Send `Authorization: Token ...` or a malformed `Bearer` value.
  - Assert `401 Unauthorized`.

- [ ] Reject a JWT with an invalid signature.
  - Create a token signed with a different secret but the same issuer/claim shape.
  - Assert `401 Unauthorized`.

- [ ] Reject a token without the required `id` claim.
  - Sign an otherwise valid token that omits the claim.
  - Assert authentication does not create a principal and the endpoint returns `401 Unauthorized`.
  - Exercises the null branch in `JwtService.config.validate`.

- [ ] Reject a token with a non-numeric `id` claim.
  - Sign a token with an invalid claim type and assert `401 Unauthorized`.

- [ ] Reject an expired JWT.
  - Sign a token with an expiration in the past and assert `401 Unauthorized`.

- [ ] Add focused tests for `KJWTSignError.toJwtGeneration` only if a stable test seam can be introduced.
  - Do not make production configuration invalid merely to satisfy coverage.
  - Prefer extracting the mapping into an internal function or injecting a signer only if signing-failure behavior is an important supported contract.

## 5. Production startup

**Priority: low**

[`main()`](src/main/kotlin/io/github/nomisrev/Main.kt) intentionally starts a long-running Netty server and waits for cancellation. It should not be invoked directly by the ordinary test suite.

- [ ] Keep `main()` excluded from direct test requirements unless a controlled application-lifecycle test harness is introduced.
- [ ] Retain the existing `testServer` coverage of `Application.app`, route installation, authentication configuration, and readiness installation.
- [ ] If startup configuration becomes more complex, extract environment loading and server construction into finite, injectable functions and test those functions instead of testing the blocking entry point.

## 6. Persistence adapter invalid-data paths

**Priority: medium-low**

[`src/main/kotlin/io/github/nomisrev/env/persistence.kt`](src/main/kotlin/io/github/nomisrev/env/persistence.kt) decodes SQL values through validated domain types. The normal integration tests cover valid database values; add focused tests for database-corruption or migration-safety behavior only if those failures must be supported explicitly.

- [ ] Add focused tests for `requireAll`.
  - Provide a transformation that produces multiple validation messages.
  - Assert that `IllegalArgumentException` contains all messages in order.

- [ ] Test invalid decode values for each adapter.
  - Exercise invalid `Title`, `Description`, `Body`, `Email`, and `Username` values through the corresponding `ColumnAdapter.decode` path.
  - Assert decode fails with the expected aggregated validation message.

- [ ] Test valid adapter round trips.
  - For every domain adapter, assert `decode(encode(value)) == value`.
  - This gives direct regression coverage to the conversion boundary without depending on generated SQLDelight implementation details.

- [ ] Decide whether invalid persisted values are a recoverable application error.
  - If yes, replace the current `IllegalArgumentException` boundary with a typed failure and add route/service tests for it.
  - If no, keep focused adapter tests and document that corrupted persisted data fails fast.

## Coverage-report maintenance

**Priority: medium**

- [ ] Exclude SQLDelight-generated classes from Kover verification metrics.
  - Exclude `io.github.nomisrev.sqldelight.**` and `io.github.nomisrev.sqldelight.ktorarrowsample.**` from quality-gate rules.
  - Continue running integration tests against the generated persistence implementation; only the generated implementation should be excluded from the reported target.

- [ ] Define explicit Kover verification thresholds for handwritten code.
  - Start with a threshold slightly below the current handwritten baseline.
  - Raise it only after adding the high-priority domain-error, ownership, user-failure, and JWT tests above.

- [ ] Run `./gradlew -q koverHtmlReport` after each coverage-focused change.
  - Review [`build/reports/kover/html/index.html`](build/reports/kover/html/index.html).
  - Confirm the added test reaches the intended error branch rather than only increasing generated-code coverage.
