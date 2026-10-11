# Starve

A self-hosted, single-user personal feed aggregator (RSS-first: YouTube,
SoundCloud, news). Built with Kotlin, Spring Boot, Thymeleaf/htmx and SQLite.
Feed features are tracked in the GitHub issues; the current build is the project
skeleton (a placeholder home page, `/actuator/health`, and SQLite persistence).

## Run locally

Needs JDK 21+.

```
./mvnw spring-boot:run
```

Open <http://localhost:8080>. The database is `./data/starve.db` (override with
`STARVE_DB_PATH` or `--starve.db.path`).

## Run with Docker

```
docker compose up --build
```

The app listens on port 8080; the SQLite file lives on the `starve-data` volume
mounted at `/data`, so data survives restarts.

## Develop

- `./mvnw -B verify` runs unit, architecture and Cucumber acceptance tests plus
  coverage, detekt and ktlint.
- See `docs/architecture.md` and `docs/testing.md`; the spec-first workflow is in
  `.claude/workflow.md`.
