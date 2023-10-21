# Contributing to Ktor Arrow Example

I want to encourage everyone to contribute to Ktor Arrow Example,
if you're unsure if you did everything correctly please just submit a PR, and ask for advice.
I'll be more than happy to help you out!

If you have any suggestions, or ideas feel free to open an issue or open a PR immediately.
I'm happy to receive any suggestions, or feedback and we can discuss on the issue or PR on how to procced.

## Implementing a new feature

All issues are end-to-end, meaning that when solving an issue you probably need to:
1. Add respective operation in the repository layer (repo package).
2. (optional) If operation is more complex than a single database operation, add service layer function that implements the operation
3. Add route that handles, and validates requests, and calls the respective service or repository layer function.
4. Writing a couple of tests that verify the correct behavior. See [writing tests](#writing-tests)

Step 2 is optional because I personally don't like unnecessary wrapping repository layer functions in a service layer.

## Writing tests

When writing tests we typically want to test more than the happy path. So ideally we test following scenarios:
 - Check happy path
 - Check with incorrect, or missing, query/path parameters
 - Check different error responses, and/or not found responses.
 - If route is authenticated: test what happens if unauthorised 

## Creating new files

The project currently uses the traditional packaging by layer approach,
the app exists out of 3 _main_ layers:

- repo: All persistence layer code belongs here. 1 file/interface per table.
- service: All service layer code, this is code combining persistence layers in higher-level functionality
- routes: All Ktor routing belongs in this package. Files are split amongst their respective endpoint

## Formatting the project

The project uses Spotless and KtFmt for formatting the code.
To format the code simply run `./gradlew spotlessApply`, this is being checked and verified on GitHub Actions.

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
