# Frontend Plan — Server-Rendered HTMX UI for Conduit

This document plans a server-rendered HTML frontend for the existing RealWorld ("Conduit") JSON API in this
repository, built with Ktor's `kotlinx.html` DSL plus its experimental HTMX modules (`ktor-htmx`,
`ktor-htmx-html`, `ktor-server-htmx`, Ktor 3.5.1). It also contains a **critical review of the current HTMX DSL**,
based on direct inspection of the shipped jars (not just the docs page), with concrete proposals we can raise
with the Ktor team.

---

## 1. Goals & constraints

- Ship a browser-usable UI (feed, article, editor, auth, profile, settings) reusing the **existing domain layer**
  (`UserService`, `ArticleService`, `TagPersistence`, `UserPersistence`) — no second implementation of business
  rules, no separate BFF/HTTP client hop. The web layer is a second *presentation* layer next to the existing
  Spine/JSON one, calling the same Kotlin services in-process.
- Keep the JSON API untouched. Additive only: new routes, new package, new plugins installed conditionally.
- Follow this repo's conventions: feature-first packages, `*Routes.kt` thin adapters, no persistence/business logic
  in routes, `Raise<DomainError>` for failures, `Dependencies`/`ResourceScope` wiring in `env/`.
- Progressive enhancement: every page must render fully on a plain GET (no JS required for first paint); HTMX only
  upgrades interactions (pagination, favorite/follow toggles, comments, tag filters, form validation feedback).
- Use HTMX 2.x from a vendored static file (no CDN dependency in prod), served via Ktor's static content plugin.

## 2. High-level architecture

```
Browser (HTMX 2.x, ~14kb, no build step)
   │  hx-get/hx-post/hx-target/hx-swap
   ▼
Ktor routing
   ├── /api/**            existing Spine JSON routes (unchanged)
   └── /**                new "web" routes (kotlinx.html + ktor-htmx-html)
          ├── full-page GET routes  → layout(shell) { page content }
          └── fragment routes (hx.*)→ partial kotlinx.html, no shell
                    │
                    ▼
          existing UserService / ArticleService / TagPersistence
                    │
                    ▼
          SqlDelight persistence (unchanged)
```

No new persistence, no new services. The web layer is purely a rendering + session adapter over what exists.

## 3. Package layout (follows `references/package-structure.md`)

New feature-first package `web/` at `io.github.nomisrev.web`, mirroring the existing `users/`, `articles/` style:

```
io.github.nomisrev.web/
├── WebModule.kt            # Application.webRoutes(dependencies): installs static assets + mounts page routers
├── WebSession.kt           # Browser session: JWT stored in a signed/HttpOnly cookie, WebPrincipal, login/logout helpers
├── Layout.kt               # HTML shell (head, nav, flash messages), fragment vs full-page rendering helper
├── HxSupport.kt            # Thin helpers filling gaps in ktor-server-htmx (see §6), e.g. triggerEvent(), swapOob()
├── home/
│   ├── HomeRoutes.kt        # GET "/" (global/personal feed tabs, tag sidebar), fragment: GET "/articles" (hx list+paging)
│   └── HomeView.kt          # kotlinx.html templates for feed page + article-list fragment
├── auth/
│   ├── AuthRoutes.kt        # GET/POST "/login", GET/POST "/register", POST "/logout"
│   └── AuthView.kt
├── articles/
│   ├── ArticleRoutes.kt     # GET "/article/{slug}", GET/POST "/editor(/{slug})", POST "/article/{slug}/favorite" (fragment)
│   │                        # POST "/article/{slug}/comments" + DELETE ".../comments/{id}" (fragment)
│   └── ArticleView.kt
├── profiles/
│   ├── ProfileRoutes.kt     # GET "/profile/{username}", POST "/profile/{username}/follow" (fragment)
│   └── ProfileView.kt
└── settings/
    ├── SettingsRoutes.kt    # GET/POST "/settings"
    └── SettingsView.kt
```

`WebModule.kt` is the thin orchestrator (same shape as `Main.kt#app`):

```kotlin
fun Route.webRoutes(d: Dependencies) {
    homeRoutes(d.articleService, d.tagPersistence)
    authRoutes(d.userService)
    articleRoutes(d.articleService, d.userService)
    profileRoutes(d.userPersistence)
    settingsRoutes(d.userService)
}
```

wired from `Main.kt` next to the existing `userRoutes(...)`, `articleRoutes(...)`, etc. Static assets
(`/static/htmx.min.js`, `/static/app.css`) via `staticResources("/static", "static")`.

## 4. Session/auth bridge

The JSON API is stateless Bearer/JWT (`Authorization: Token ...`). Browsers need a cookie-based session:

- On successful `/login` or `/register` form POST, call `userService.login`/`register` directly (same as
  `UserRoutes.kt`), then set the returned `JwtToken` in an **HttpOnly, `SameSite=Lax`, `Secure` (prod)** cookie
  (`install(Sessions)` with a signed/encrypted `CookieSession(token: String)`, using Ktor's session plugin — not a
  bespoke cookie parser).
- A `WebSession` plugin/interceptor reads that cookie, verifies it with the existing `JwtConfig<JwtContext>`
  verifier (reuse `jwtService.config`, do not duplicate JWT logic), and exposes `call.webPrincipal: JwtContext?`
  to route handlers — mirroring `auth/JwtService.kt`'s existing `principal` extension, but sourced from a cookie
  instead of the `Authorization` header.
- Logout clears the cookie. No server-side session store needed (JWT is self-contained), keeping `Dependencies`
  unchanged (no new resource to release).
- CSRF: since state-changing calls (favorite, follow, comments, editor) go through `hx-post`/`hx-delete` from
  same-origin HTML forms/buttons with a cookie-based session, add the standard mitigation — either
  `SameSite=Strict/Lax` cookie (covers the common case) plus checking `HX-Request: true` is present for
  state-changing routes (HTMX always sets it, plain cross-site `<form>` posts never do), or a hidden CSRF token
  field for the small number of `<form>` POSTs (login/register/settings) that don't go through HTMX.

## 5. Rendering strategy

- `Layout.kt` exposes two entry points:
  - `HTML.page(session: WebSession?, title: String, content: BODY.() -> Unit)` — full shell: `<head>` (CSS, htmx.js,
    `hx-boost="true"` on `<body>` for automatic full-page-nav upgrades), nav bar (login state), `<main>` slot, flash
    message region (`#flash`, target of `HX-Trigger`-driven toasts, see §6).
  - `RoutingContext.respondFragment(content: FlowContent.() -> Unit)` — renders only the inner fragment, used by
    every `hx.*`-scoped handler (see §6 for why this must be a manual `if (call.request.hx-check)` today).
- Every page route implements **both** code paths from one shared template function, e.g.
  `ArticleView.articleCard(article, principal)`, reused by: the full feed page, the `hx-get` "load more" fragment,
  and the single-article page — no duplicated markup.
- List endpoints (feed, tag filter, pagination, "load more") are implemented as `hx-get` fragment routes returning
  just the `<article>` cards + a new "load more" trigger row (`hx-swap="beforeend"` for infinite scroll, or
  `hx-target="#article-list" hx-swap="outerHTML"` for tab/tag switches).
- Mutations (`favorite`, `follow`, `add/delete comment`) are `hx-post`/`hx-delete` fragment routes returning just
  the updated button/comment-list fragment (`hx-swap="outerHTML"` on the button itself) — this is the classic
  HTMX "self-updating widget" pattern.
- Server-side validation errors (register/login/editor forms) re-render the form fragment with inline error text
  next to fields, returned with `hx-target` pointed at the form and `hx-swap="outerHTML"`, using the *same*
  `accumulate`-based `Validation.kt` this project already has — errors surface as `IncorrectInput` and we map
  `NonEmptyList<InvalidField>` to per-field `<span class="error">` nodes instead of `GenericErrorModel` JSON.

## 6. Where we must paper over the current HTMX DSL

(Full critique, and what was actually shipped, in §7.) In short, `io.github.nomisrev.htmx` (implemented already,
see §7.6) adds:

1. `HXResponseHeaders.retarget`/`.reswap`/`.reselect` — these three response headers are entirely missing typed
   accessors from the convenience wrapper (see §7.2).
2. `HXResponseHeaders.triggerEvent`/`.triggerEvents` (kotlinx.serialization-based) — because
   `HX-Trigger`/`HX-Trigger-After-Swap`/`HX-Trigger-After-Settle` (needed for our flash-message/toast pattern) have
   **no typed setters at all** in the server module either. Building these also surfaced that `set` on this type
   is append-only and `remove` is unimplemented (throws) — see §7.6 for the two confirmed bugs this forced us to
   design around.
3. `HxAttributes.swap { }` / `.trigger { }` typed builders (e.g. `outerHTML settle:300ms`) so templates don't
   hand-concatenate swap/trigger modifier strings — and, in the process of testing them, we confirmed
   `HxSwap.innerHtml` itself ships the wrong literal upstream (`"innerHtml"` instead of the htmx-required
   `"innerHTML"`), pinned as a regression test rather than silently worked around.
4. `HxAttributes.vals`/`.headers` JSON builders and `sseConnect`/`sseSwap`/`wsConnect`/`wsSend` attributes, since
   `hx-vals`/`hx-headers` otherwise require hand-written JSON strings and SSE/WS attributes have no typed setters
   at all (see §7.1).
5. `HxRoute.boosted { }` / `.historyRestoreRequest { }` route matchers and `RoutingCall.respondHtmxAware(fragment,
   page)`, filling the routing and fragment-vs-page gaps described in §7.3/§7.4.

None of these were hard to write (~250 LOC total across `io.github.nomisrev.htmx`, with a matching test suite),
but they are exactly the kind of boilerplate — and, in three cases, exactly the kind of latent bug — the official
module's "type-safe HTMX" pitch is supposed to remove.

## 7. Critical review of the Ktor HTMX DSL (`ktor-htmx` / `ktor-htmx-html` / `ktor-server-htmx`, 3.5.1)

This is based on decompiling/inspecting the actual jars (`javap`-level, via bytecode introspection), not just the
`ktor.io` docs page, which undersells several rough edges.

### 7.1 `HxAttributes` (HTML DSL) is a flat string bag, not a typed builder

`attributes.hx { ... }` (`io.ktor.htmx.html.HxAttributes`) exposes **73 members**, and every single attribute —
`get`, `post`, `swap`, `trigger`, `vals`, `params`, `headers` (only via `on`/`set`), `sync`, `select`, `on(event,
script)` — is typed as plain `String` (or `Boolean` for a handful of flags). Concretely:

- `swap: String` even though the same core module ships `HxSwap` with named constants (`innerHtml`, `outerHtml`,
  `beforeBegin`, ...). There is no `enum class HxSwap` accepted by `HxAttributes.swap` — the constants are just
  `String` too, so `attributes.hx { swap = "outerHtml" }` (a typo — real value is `"outerHTML"`) compiles fine and
  silently no-ops in the browser. **The constants and the setter are not linked by the type system at all.**
- `trigger: String` — htmx's most expressive attribute (`"click"`, `"keyup changed delay:500ms"`,
  `"every 2s"`, `"load, revealed"`, `from:`/`target:`/`consume`/`queue:` modifiers) is entirely un-typed. No
  builder exists comparable to Ktor's own `CacheControl`/`ContentDisposition` header builders.
- `vals`/`params`/`headers` (hx-vals, hx-headers) are meant to carry **JSON objects** — the DSL takes a raw
  `String`, so callers must hand-write JSON literals (`vals = """{"id": $id}"""`) with zero escaping help, in a
  project that otherwise uses kotlinx.serialization everywhere else. A one-line
  `fun HxAttributes.vals(json: JsonElement)` (or `vals(vararg pairs: Pair<String, Any?>)`) using the
  already-transitive kotlinx-serialization dependency would remove an entire class of "works until someone's title
  contains a quote" bugs.
- `HxAttributes.On` exists as a value-class wrapper around `Map<String, String>` (`getOn-6sA4otk()`,
  mangled name confirms it's an inline value class) but is not exposed as anything richer than `on(event, script)`
  — i.e., no compile-time link to real event names, and it invites embedding raw JS strings inline
  (`hx-on:click="..."`) with no escaping/CSP-nonce story, which is a real XSS foot-gun for anyone interpolating
  user content into that string. There is no guidance/helper differentiating "static script" from
  "must-never-contain-user-data" here.
- `vals` **and** `vars` both exist (`getVars`/`setVars`) even though `hx-vars` was deprecated by htmx 1.x in favor
  of `hx-vals` years ago — the DSL should not offer a deprecated attribute with zero `@Deprecated` annotation.
- No attribute helpers exist for `hx-sse-connect`/`hx-sse-swap`/`hx-ws-connect`/`hx-ws-send` at all, despite the
  Ktor docs page explicitly advertising "SSE and WebSockets... without writing JavaScript" as a headline HTMX
  feature. Today those require dropping to `attributes["hx-ws"] = "connect:/chat"` raw strings, which defeats the
  entire point of having a typed DSL module.

### 7.2 Server response header builder (`HXResponseHeaders`) is missing most of the documented headers

`call.response.hx` (`io.ktor.server.htmx.HXResponseHeaders`) only exposes typed get/set for **5** of the
documented response headers:

```
Location, PushUrl, Redirect, Refresh, ReplaceUrl
```

The docs page (and the sibling `io.ktor.htmx.HxResponseHeaders` *constants* object in `ktor-htmx`) list eleven:
`Location, PushUrl, Redirect, Refresh, ReplaceUrl, Reswap, Retarget, Reselect, Trigger, TriggerAfterSettle,
TriggerAfterSwap`. **`Reswap`, `Retarget`, `Reselect` and all three `Trigger*` variants have no typed accessor** —
exactly the headers most needed for our flash-message/toast pattern and for server-driven "swap somewhere else"
responses (a very common HTMX idiom: "this POST failed validation, retarget the response at the form and reswap
outerHTML"). Users must fall back to `call.response.header("HX-Retarget", "#form")` string literals, silently
losing the entire premise of "avoid magic strings" the docs promise. This asymmetry (constants defined in
`ktor-htmx`, but not wired into the `ktor-server-htmx` convenience object) looks like an oversight rather than a
deliberate omission, and would be a good, narrowly-scoped PR to send upstream.

### 7.3 Server-side routing (`Route.hx`, `target`, `trigger`) is feature-incomplete versus the request headers it advertises

`HXRequestHeaders` (`io.ktor.server.htmx`) exposes `isBoosted`, `isHistoryRestore`, `currentUrl`, `prompt`,
`targetId`, `triggerId`, `triggerName` as request-side accessors. But the routing DSL (`Route.hx { }`, and the
`target(id) { }` / `trigger(id) { }` builders nested inside it) only lets you branch on **target** and
**trigger id**, both via **exact string equality** against the route selector. Concretely missing:

- No `hx.boosted { }` / `hx.notBoosted { }` child-route builder, despite `isBoosted` existing as a header
  accessor — you cannot route "boosted nav vs. explicit AJAX call" differently without an `if` inside the handler.
- No `hx.historyRestoreRequest { }` builder either, same gap.
- `target`/`trigger` match by **exact** header value only. In real usage `hx-target` is a CSS selector
  (`"#result"`), and the incoming `HX-Target` header htmx sends is the **element id without `#`** — fine for
  simple cases, but there's no matcher for "target is inside container X" or for matching against a set of ids, so
  non-trivial layouts (e.g., the same fragment route serving both a modal and an inline slot) still need manual
  `if (call.request.headers[HxRequestHeaders.Target] in setOf(...))` branching inside the handler body, defeating
  the purpose of having declarative routing at all.
- `HxRoute` is a Kotlin **inline value class** wrapping `Route` (`constructor-impl(Route): Route`,
  `box-impl`/`unbox-impl` are visible in the compiled API). That's a reasonable zero-cost trick, but it currently
  leaks: IDE completion on the `hx { ... }` receiver shows synthetic `-impl` suffixed members (`getAttributes-impl`
  etc. appear in the class file, and `equals`/`hashCode`/`toString` are inherited value-class boilerplate) that
  add noise for anyone browsing the API in an IDE without javadoc/sources correctly attached — worth confirming
  KDoc/sources jars are always shipped so this stays invisible.
- No composable way to combine matchers, e.g. "boosted GET to `/articles` from target `#feed`" — each of
  `hx`, `target`, `trigger` nests a *new* child `Route`/`RouteSelector`, so combining them means manually nesting
  `hx { target("#feed") { trigger("tab-changed") { get { ... } } } }`, which is fine for 1–2 levels but has no
  single "match all of" convenience akin to Ktor's own `route` + multiple `param`/`header` selector composition.

### 7.4 No first-class "respond differently for HTMX vs. full page load" helper

The single most common HTMX server-side pattern — "if this is an `HX-Request`, render just the fragment; otherwise
render the full page with the same content embedded" — has **no built-in helper** in `ktor-server-htmx`. The
module gives you the ability to route *only* HTMX requests to a *different route* (`hx.get { }` next to a sibling
plain `get { }`), which is a reasonable primitive, but the very common alternative pattern (same URL, same route,
same handler, just skip the `<html>`/`<head>`/nav shell when `HX-Request: true`) requires everyone to write their
own `if (call.request.hx-check) fragment() else page { fragment() }` — exactly what we do in `Layout.kt` (§5). A
`respondHtmxAware(fragment = { }, page = { fragment() })`-style helper in `ktor-htmx-html` (mirroring how
`ContentNegotiation` picks a format) would remove a huge amount of copy-pasted boilerplate across every real HTMX
app built on Ktor, and is IMO the single highest-value addition to propose upstream.

### 7.5 No integration story with typed routing (Locations/Resources or third-party typed routers like Spine)

This project defines its JSON API with Spine's typed `Endpoint`/`Resource` model (`Api.kt`), not raw
`routing { get("/path") }`. There's no equivalent typed-href generation for the HTML side: `HxAttributes.get =
"/articles/$slug"` is a raw string with no compile-time link to the route that will actually serve it, so renaming
a web route silently breaks every `hx-get`/`hx-post`/`<a href>` that pointed at it. Ktor's own `Resources` plugin
solves exactly this for regular links (`href(MyResource(id))`); an equivalent `hx-get { href(SomeResource(id)) }`
overload accepting a typed `Resources`-annotated class (instead of only `String`) would close this gap and is a
natural, additive extension point (`fun HxAttributes.get(resource: Any) { get = href(resource) }` given access to
the current `Application`/`RoutingContext`). Because we use Spine here instead of `Resources`, we'll write our own
tiny `fun HxAttributes.get(endpoint: Api.SomeEndpoint, vararg args)` shim in `HxSupport.kt`, but this is exactly
the kind of adapter point the upstream module should expose a hook for (e.g. accepting any `UrlBuilder`-like
interface) instead of hard-coding `String`.

### 7.6 Local utilities implemented (`io.github.nomisrev.htmx`)

Rather than wait on upstream, every gap in §7.1–§7.5 has a small, tested, additive workaround already implemented
in `src/main/kotlin/io/github/nomisrev/htmx/`, exercised by `src/test/kotlin/io/github/nomisrev/htmx/`:

| File | What it adds |
|------|--------------|
| `HxResponseHeadersSupport.kt` | Typed `retarget`/`reswap`/`reselect` properties; `triggerEvent`/`triggerEvents` (kotlinx.serialization-backed `HX-Trigger*` builders) |
| `HxSwapBuilder.kt` | `HxAttributes.swap(HxSwap.outerHtml) { settleDelay(...) }` typed builder instead of hand-concatenated `hx-swap` strings |
| `HxTriggerBuilder.kt` | `HxAttributes.trigger("keyup") { changed(); delay(500.milliseconds) }` typed builder, plus `every(...)` and `triggers(...)` for combining several |
| `HxJsonAttributes.kt` | `HxAttributes.vals(typedValue)` / `vals("k" to JsonPrimitive(...))` / `headers(...)` JSON builders (no more hand-written `hx-vals` JSON strings); `sseConnect`/`sseSwap`/`wsConnect`/`wsSend` typed attributes; `vars(...)` re-declared with `DeprecationLevel.ERROR` so nobody reaches for the htmx-deprecated `hx-vars` by accident |
| `HxRouteMatchers.kt` | `HxRoute.boosted { }` / `HxRoute.historyRestoreRequest { }` child-route builders, built on the same `HttpHeaderRouteSelector` primitive `HxRoute.target`/`.trigger` already use internally |
| `HxRespond.kt` | `RoutingCall.respondHtmxAware(fragment, page)` - the missing "fragment if `HX-Request`, else full page" helper |
| `HxHref.kt` | `HxHref` interface + `HxAttributes.get/post/put/patch/delete(href: HxHref)` overloads, a minimal typed-href stand-in |

Building and testing these surfaced **two additional, previously undocumented runtime bugs** in `ktor-server-htmx`
3.5.1 (both pinned by regression tests in `HxResponseHeadersSupportSpec.kt`), beyond the earlier design critique:

- **`HXResponseHeaders.remove(String)` is unimplemented and throws `IllegalStateException("Not implemented")` at
  runtime.** It's a real, callable, public method (`call.response.hx.remove("HX-Retarget")` compiles fine) that
  crashes the instant it's invoked. The HTML-DSL sibling, `HxAttributes.remove` (`ktor-htmx-html`), works correctly
  - only the server response-header side is broken. This is why the local `retarget`/`reswap`/`reselect`
  extensions above are one-shot, non-clearing `String` setters instead of `String?` with remove-on-null, exactly
  mirroring how the module's own `location`/`pushUrl`/`redirect`/`replaceUrl` properties behave.
- **`HXResponseHeaders.set(String, String)` is an *append*, not a replace.** Decompiling shows it calls
  `ResponseHeaders.append(name, value, safeOnly = false)` - because `ApplicationResponse.headers` itself only ever
  exposes `append`, never a real "set". Calling the same header-name setter twice in one response (directly, or via
  a naive `get`-then-`set` read-modify-write "merge" - which is exactly what a first, more obvious implementation
  of `HX-Trigger` event-merging would do) does not overwrite the first value: it silently emits two separate
  `HX-Trigger` header lines on the wire, and both `HXResponseHeaders.get` and a normal HTTP client's by-name header
  lookup only ever surface the *first* one, so the second call's data is silently lost. `triggerEvents(vararg ...)`
  is designed around this constraint (build the full JSON object once, write the header exactly once) rather than
  exposing a `triggerEvent` that claims to "merge into whatever was set before", which would be a believable but
  broken API on top of this backing store.

Both are independent of the design critique in §7.1–§7.5 - they are correctness bugs, reproducible today against
3.5.1, and worth filing as upstream issues on their own (see #10/#11 in the table below).

### 7.7 Summary — proposals worth raising with the Ktor team

| # | Gap | Proposed fix |
|---|-----|--------------|
| 1 | `HXResponseHeaders` missing `Reswap`/`Retarget`/`Reselect`/`Trigger*` typed accessors | Add the missing 6 typed properties; small, backwards-compatible, mirrors existing 5 |
| 2 | `HxAttributes.swap`/`trigger` are raw `String` | Add optional typed overloads / small builder DSLs (`hxSwap { }`, `hxTrigger { }`) layered over the existing string setter |
| 3 | `hx-vals`/`hx-headers` take raw JSON strings | Add `fun HxAttributes.vals(vararg pairs: Pair<String, Any?>)` / `JsonElement` overload using kotlinx.serialization (already a transitive dep of `kotlinx-html`) |
| 4 | `hx-vars` offered with no deprecation warning | Mark `getVars`/`setVars` `@Deprecated`, matching upstream htmx guidance |
| 5 | No `hx-sse-*`/`hx-ws-*` attribute helpers | Add matching properties to `HxAttributes`, consistent with the module's SSE/WS marketing claim |
| 6 | `Route.hx { }` only matches on `target`/`trigger` id | Add `boosted { }` / `historyRestoreRequest { }` child-route builders, mirroring existing `HXRequestHeaders` accessors |
| 7 | No fragment-vs-page response helper | Add a `respondHtmxAware(fragment, page)`-style helper to `ktor-htmx-html` |
| 8 | No typed-href hook for `hx-get`/`hx-post`/etc. | Accept a small `UrlBuilder`-like abstraction (or at least `Resources`-plugin instances) alongside `String` overloads |
| 9 | `HxAttributes.On` value class adds no real safety over a raw string | Either drop the wrapper (it adds indirection without validation) or actually validate/allowlist event names |
| 10 | **Bug:** `HXResponseHeaders.remove(String)` throws `IllegalStateException("Not implemented")` | Implement it (there's no obvious reason it can't wrap the same `ResponseHeaders` the getter already reads), or drop it from the public `StringMap`-like surface so it can't be called at all |
| 11 | **Bug:** `HXResponseHeaders.set(String, String)` is append-only, silently duplicating headers on a second call for the same name | Track already-set header names internally and replace instead of append, or clearly document these as write-once properties |
| 12 | **Bug:** `HxSwap.innerHtml` is wired to the literal `"innerHtml"` instead of the htmx-mandated `"innerHTML"` (every sibling constant uses the correct htmx-facing spelling) | One-line fix: correct the constant's value to `"innerHTML"` |

We'll track these as upstream issues against `ktorio/ktor` - #10-#12 are outright bugs with regression tests already
in this repo (`HxResponseHeadersSupportSpec.kt`, `HxAttributeBuildersSpec.kt`) ready to attach as reproductions;
#1-#9 are design/API-completeness proposals.


## 8. Testing plan

- Reuse `ktor-server-test-host` (already a test dependency) with `testApplication { }`, hitting web routes and
  asserting on returned HTML via a lightweight HTML query (Jsoup, added as a test-only dependency) rather than
  string `contains` checks — assert on element attributes (`hx-get`, `hx-target`) and structure, not raw markup.
- One test class per feature package mirroring `src/test/kotlin/io/github/nomisrev/<feature>` structure already
  in place for the JSON API.
- Cover: full-page render for anonymous vs. authenticated nav state, fragment-only render when `HX-Request: true`
  is sent, favorite/follow toggle round-trip, comment add/delete fragment swap, validation error fragment
  rendering (reusing existing `Validation.kt` failure cases), login/logout cookie lifecycle.
- No headless-browser/E2E layer in scope for v1 (HTMX behavior itself is out of process); if we later want real
  browser coverage, Playwright against a running `testApplication` instance is the natural next step.

## 9. Delivery phases

1. **Scaffolding**: add `ktor-htmx`/`ktor-htmx-html`/`ktor-server-htmx` deps (`@OptIn(ExperimentalKtorApi::class)`
   at file level where used), `Layout.kt` shell, static asset serving, `WebSession.kt` cookie bridge. No pages yet.
2. **Read-only pages**: home/feed (global + tag filter, pagination fragment), article view, profile view — no
   auth required, proves the rendering + fragment pattern end-to-end.
3. **Auth**: login/register/logout, nav reflects session state.
4. **Authenticated interactions**: favorite/unfavorite, follow/unfollow, comments (add/delete) — all as
   self-updating HTMX fragments.
5. **Editor + settings**: create/update article, update profile — form validation fragments reusing
   `Validation.kt`.
6. **Polish**: flash messages via `HX-Trigger` (once §6 helper #2 exists), `hx-boost` on nav for SPA-like feel,
   loading indicators (`hx-indicator`), optimistic UI where safe.
