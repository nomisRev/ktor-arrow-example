# Getting started

This page explains how to run the project and how to preview the documentation site locally.

## Running the project

You need Docker and a JDK installed. Start PostgreSQL, then run the Ktor server:

```bash
docker-compose up -d
./gradlew run
```

Verify the server is up through the readiness endpoint:

```bash
curl -i 0.0.0.0:8080/healthz/readiness
```

!!! warning
    `./gradlew run` doesn't properly run JVM shutdown hooks, so the port may remain bound after stopping.

## Running the tests

Tests run against a PostgreSQL Testcontainer, so Docker needs to be available:

```bash
./gradlew test
```

## Previewing the documentation

The site is built with [MkDocs](https://www.mkdocs.org/) and the Material theme.
Create and activate a virtual environment, then install the documentation dependencies:

```bash
python -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements-docs.txt
```

Start the live preview server:

```bash
source .venv/bin/activate
mkdocs serve
```

Build the static site output:

```bash
source .venv/bin/activate
mkdocs build --strict
```

The generated site is written to `site/`, which is ignored by Git.
