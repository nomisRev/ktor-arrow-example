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
