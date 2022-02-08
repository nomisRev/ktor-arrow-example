# Ktor Arrow Example

A microservice example with Ktor, Arrow.

Other technologies used:
 - SqlDelight for the persistence layer.
 - Kotest for testing

## Running the project

To run the project, you first need to start the environment.
This can be done with `docker-compose up`,
and then you can start the Ktor server with `./gradlew run`.

```shell
docker-compose up
./gradlew run
curl 0.0.0.0:8080/health
```

## Endpoints

 - GET /health: returns version of the connected postgres
