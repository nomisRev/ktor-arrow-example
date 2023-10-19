# Ktor Arrow Real World Example

> While most "todo" demos provide an excellent cursory glance at a framework's capabilities, they typically don't convey
> the knowledge & perspective required to actually build real applications with it.

*Read
the [full blog post announcing RealWorld on Medium.](https://medium.com/@ericsimons/introducing-realworld-6016654d36b5)*

Over 100 implementations have been created using various languages, libraries, and frameworks.

Explore them on [**CodebaseShow**](https://codebase.show/projects/realworld).

In order for Real World back-end and front-end examples to be able to work together we need to follow a
predefined [openapi.yml](https://github.com/gothinkster/realworld/blob/main/api/openapi.yml).

## Looking to contribute?

If you're looking to contribute check-out [CONTRIBUTING.MD](CONTRIBUTING.MD).
Also, this project participates in hacktoberfest!

I will provide personal guidance, and support to everyone that is interested in learning about Ktor, Arrow and Kotlin by
contributing to this repository!

## How it works

This example uses [Kotlin](https://kotlinlang.org/docs/home.html) with [Ktor](https://ktor.io/)
and [Arrow](https://arrow-kt.io/) as the main building blocks.
Other technologies used:

- [SqlDelight](https://cashapp.github.io/sqldelight/) for the persistence layer
- [Kotest](https://kotest.io/) for testing
- [GraalVM](https://www.graalvm.org/) to build an optional native binary

## Running the project

To run the project, you first need to start the environment.
This can be done with `docker-compose up`,
and then you can start the Ktor server with `./gradlew run`.

```shell
docker-compose up
./gradlew run
curl -i 0.0.0.0:8080/readiness
```

Beware that `./gradlew run` doesn't properly run JVM Shutdown hooks, and the port remains bound.

### GraalVM

The project can be built into a [native image](https://www.graalvm.org/latest/reference-manual/native-image/) with GraalVM.

#### Getting started
1. Ensure a suitable [GraalVM is installed](https://www.graalvm.org/downloads/)
   * Tested versions include:
     * _Oracle GraalVM 21+35.1 (project default)_
     * _Oracle GraalVM 17.0.8_
2. Build using `./gradlew nativeCompile`
   * An image will be built at `/build/nativeCompile/ktor-arrow-sample`
3. Start `./gradlew nativeRun` (remember to `docker-compose up`)
   * Verify with `curl -i 0.0.0.0:8080/readiness`

#### Known issues
* Ktor uses reflection internally when calling either `call.receive()` or `call.respond()`
  * As a workaround, use `call.receiveText()`/`call.respondText()` and (de)serialize manually
* `nativeTestCompile` and `nativeTestRun` are not supported currently

#### Troubleshooting & helpful resources
* [Configuring GraalVM Gradle buildtool](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#configuration)
* [Reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/)