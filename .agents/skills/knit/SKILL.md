---
name: knit
description: >
  Create and maintain executable Kotlin documentation with KotlinX Knit in this repository.
  Use when adding or changing tutorial Markdown, Knit directives, generated examples or
  generated tests, Knit configuration, or the Gradle tasks that keep documentation and
  source samples synchronized.
---

# KotlinX Knit skill

KotlinX Knit is a Gradle plugin that extracts Kotlin code from Markdown or Kotlin source
comments, generates compilable example files and tests, and updates selected Markdown
markup. It is directive-based; it does not fully parse Markdown or HTML.

## Repository setup

This repository uses Knit `0.5.1`:

- The version is `knit` in `gradle/libs.versions.toml`.
- The `org.jetbrains.kotlinx.knit` plugin is applied through the
  `libs.plugins.kotlinx.knit` alias.
- `build.gradle.kts` adds `org.jetbrains.kotlinx:kotlinx-knit:0.5.1` to the buildscript
  classpath and configures `KnitPluginExtension.files` to process only
  `docs/tutorials/knit-testing.md` and `docs/tutorials/validation.md`.
- `knit.properties` writes examples to `src/test/kotlin/knit/examples/` in the
  `io.github.nomisrev.knit` package and generated tests to
  `src/test/kotlin/knit/generated/` in the same package.
- `kotlinx-knit-test` is a test dependency. Knit 0.5.x generates JUnit 4 tests, so the
  JUnit Vintage engine is also configured.

Do not broaden the configured file tree to all Markdown without an explicit reason.
Generated example and test sources are repository files in this project: review and
commit them together with the documentation and configuration changes.

## Gradle workflow

- `./gradlew -q knit` regenerates Markdown, examples, and tests.
- `./gradlew -q knitCheck` verifies that generated files are current; it does not create
  missing files. The plugin normally wires `knitCheck` into `check`.
- Run `./gradlew -q knit` after editing a configured document, then
  `./gradlew -q knitCheck test` before submitting.

The project applies the Kotlin plugin before Knit. When configuring another project,
Knit requires at least the Kotlin or `base` plugin first.

## Markdown examples

Knit recognizes single-line directives such as:

```text
<!--- KNIT example-validation-01.kt -->
```

and multiline directives such as `INCLUDE`, `PREFIX`, `SUFFIX`, and `TEST`. Kotlin code
in triple-backtick `kotlin` blocks is collected until a Knit example marker is reached.
Multiple blocks can be merged into one generated file. The marker consumes the code
immediately before it, so place it directly after the relevant snippet.

For this repository, use the `example-...-##.kt` naming pattern configured by Knit. The
`##` portion is numbered automatically within the document. A generated example is a
complete Kotlin source file in its generated package, so include every required import
and a `main` function when the example is tested. Do not depend on package-private
members from the Markdown source.

Example shape:

````markdown
<!--- TEST_NAME ValidationTest -->

```kotlin
fun main() {
    println("ok")
}
```

> You can get the full code [here](src/test/kotlin/knit/examples/example-validation-01.kt).

```text
ok
```

<!--- TEST -->
````

The generated test invokes the example's `main` and compares captured output. Keep
examples deterministic: avoid timestamps, network calls, databases, and unspecified
iteration order. Expected output must use a `text` block and match stdout line by line.

## Supported directives

Use directives only when they improve the generated source or tests:

- `KNIT file.kt` generates an example without exposing a file link to readers.
- A link whose target matches `knit.dir` and the configured example pattern generates or
  updates the example file and can be renumbered automatically.
- `INCLUDE` adds hidden imports or scaffolding. A filename pattern after it can target
  multiple examples. A single-line form can reuse the preceding Kotlin block.
- `PREFIX` and `SUFFIX` add hidden code before or after generated examples and support
  the same filename-pattern behavior as `INCLUDE`.
- `TEST_NAME Name` defines the generated test class for a Markdown file.
- `TEST` adds a test for the preceding example. Hidden expected output may be placed in
  the directive body instead of the document. A custom predicate receives
  `lines: List<String>`.
- `TEST LINES_START` uses the configured `verifyOutputLinesStart` comparison mode.
  Additional modes are mapped through `test.mode.<mode-name>` properties.
- `TOC` followed by `END` replaces the contents between the markers with a table of
  contents for second-level and smaller headings.

Back-to-back directives can share one comment, separated with `-----`.

## `knit.properties`

Properties are read from the directory beside the Markdown file and inherited from
parent directories up to the configured root. Paths are relative to the property file
for property values and to the Markdown file for paths written in Markdown.

Important properties include:

```properties
knit.dir=src/test/kotlin/knit/examples/
knit.package=io.github.nomisrev.knit
test.dir=src/test/kotlin/knit/generated/
test.package=io.github.nomisrev.knit
```

Other useful properties are `knit.pattern` for the example filename pattern,
`knit.include` for a FreeMarker example-file template, and `test.template` for a
FreeMarker test template. Custom templates may use arbitrary `knit.*` or `test.*`
properties.

## Kotlin source comments

Knit markup can also be embedded in `.kt` and `.kts` comments, including block comments,
`//` comments, and KDoc. In KDoc, directives and fenced Kotlin blocks are written after
the leading `*`. This is useful when the source itself is the documentation source, but
API-reference expansion is not supported inside Kotlin source comments.

## API links with Dokka

Knit can expand Markdown reference links to a project's Dokka API documentation. Build
Dokka before Knit by making the relevant task a dependency of `knitPrepare`, then set
`siteRoot` without a trailing slash. `MODULE` selects a module and one or more `INDEX`
directives select packages; close the section with `END`. Use `/module-name` when the
API docs are built from the module root project.

Do not add API-link configuration to this repository unless Dokka output and the target
site are configured as part of the same change.

## Review checklist

1. Confirm the Markdown file is included by `KnitPluginExtension.files`.
2. Keep example names unique and matching `knit.pattern`.
3. Place `KNIT` immediately after the code it should consume.
4. For executable examples, add `TEST_NAME`, an expected `text` block, and `TEST`.
5. Regenerate with `./gradlew -q knit`.
6. Inspect generated sources and tests; do not edit generated files manually.
7. Verify with `./gradlew -q knitCheck test` and commit generated files with their source
   documentation.
