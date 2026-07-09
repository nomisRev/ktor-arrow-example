# Development Rules

## Conversational Style

- Keep answers short and concise
- No emojis in commits, issues, PR comments, or code
- No fluff or cheerful filler text (e.g., "Thanks @user" not "Thanks so much @user!")
- Technical prose only, be direct
- When the user asks a question, answer it first before making edits or running implementation commands.
- When responding to user feedback or an analysis, explicitly say whether you agree or disagree before saying what you changed.

## Kotlin

### Name-based destructing (2.4.0)

Since Kotlin 2.4.0 we can use name-based destructing data classes.

```kotlin
data class Person(val age: Int, val name: String)

fun example() {
    val (name) = Person(20, "John")
    println(name) // "John"
}

fun example2() {
    val (age2, name) = Person(20, "John")
    val
}
```

### Collection literals (2.4.0)

Since Kotlin 2.4.0 we can use collection literals to create collection types.

```kotlin
val x: List<Int> = [1, 2, 3]
```

## Gradle

Always use `-q`/`--quiet` when running `./gradlew` commands to avoid noisy output.
Use fine-grained instructions rather than project-wide commands.

Prefer `./gradlew -q :module:test` over `./gradlew -q build`

### Searching dependency jars

Use the `jarSearch` Gradle task to inspect packages, types, and members in  resolved dependency jars.

Run it with `-q` to keep output clean:

```bash
./gradlew -q jarSearch --dependency <spec> [options]
```

### `--dependency` (required)
One of:
- `*` or a configuration name — search every jar on that configuration
- a Gradle coordinate `group:artifact[:version]`
- a version-catalog alias (e.g. `arrow-core`)
- a direct `.jar` path

### Options
- `--query <text>` — package, type, function, or method to look for
- `--kind <all|package|type|function|top_level_function|method>` — default `all`
  (`function`, `top_level_function`, and `method` require `--query`)
- `--configuration <name>` — configuration to search/resolve against (default `compileClasspath`)
- `--limit <n>` — max results per section (default `20`)
- `--include-non-public` — include non-public members
- `--include-synthetic` — include synthetic/compiler-generated members
- `--raw-signatures` — print raw JVM descriptors
- `--transitive <auto|true|false>` — transitive search mode (default `auto`)

### Examples
```bash
# List packages/types in a catalog dependency
./gradlew -q jarSearch --dependency arrow-core

# Find a type
./gradlew -q jarSearch --dependency arrow-core --kind type --query Either

# Find methods matching a name
./gradlew -q jarSearch --dependency "io.arrow-kt:arrow-core" --kind method --query fold
```

### Inspecting failing tests

Use the `inspectTest` Gradle task to read failing tests straight from Gradle's
JUnit XML reports (the `TEST-*.xml` files under each module's
`build/test-results`). Run a test task first, then query the results; no
intermediate file or external tooling is required.

Run it with `-q` to keep output clean:

```bash
./gradlew -q inspectTest [options]
```

### Options
- `--name <text>` — case-insensitive substring matched against the test name and full name. Omit to list every failing test.
- `--module <name>` — only match failures from this Gradle module
- `--lines <n>` — max stack-trace lines per failure; `0` shows the full trace (default `10`)

### Examples
```bash
# List every failing test
./gradlew -q inspectTest

# Show stack traces for matching tests
./gradlew -q inspectTest --name "should parse"

# Restrict to a single module
./gradlew -q inspectTest --name "should parse" --module app

# Show the full stack trace
./gradlew -q inspectTest --name "should parse" --lines 0
```
