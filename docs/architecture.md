# Architecture

## Overview

Starve is a single deployable Spring Boot (Kotlin) application: one jar, one
container. `dev.starve.StarveApplication` is the entry point. Controllers in
`web` render Thymeleaf templates (htmx is vendored at
`src/main/resources/static/htmx.min.js`, no CDN); data lives in one SQLite file
(`starve.db.path`, default `./data/starve.db`, `/data/starve.db` in Docker)
accessed with Spring `JdbcClient` (no JPA). Flyway applies
`src/main/resources/db/migration/V*.sql` on startup. Actuator exposes only
`/actuator/health`. There is no authentication (single user, self-hosted).

## Modules

All under `dev.starve`.

| Package | Responsibility | May depend on |
|---|---|---|
| `domain` | business rules, no framework code | nothing (JDK + Kotlin only) |
| `application` | use cases, orchestration | `domain` |
| `web` | controllers and templates | `application`, `domain` |
| `infra` | persistence and external adapters | `application`, `domain` |

## Feed ingestion

`domain` holds `Source`, `Feed`/`FeedItem` and `FeedRules` (item identity, URL
validity, retention cutoff). `application` defines the ports `FeedFetcher`,
`SourceRepository` and `ItemRepository`, and the services `SubscriptionService`
(subscribe/unsubscribe), `PollService` (poll all, isolating per-source failures),
`FeedIngestor` (records one fetch outcome) and `RetentionService`. `infra`
implements the ports: `RomeFeedFetcher` (JDK `HttpClient`, conditional GET, ROME
parsing, one timeout over the whole exchange) and the `Jdbc*Repository` classes;
`FeedConfig` wires the services and `SchedulingConfig` runs polling and
retention on `@Scheduled` timers. Items are unique per `(source, key)` where key
is the guid, else the link.

## Dependency rule

Dependencies point inward: `web`/`infra` -> `application` -> `domain`. Nothing
depends on `web`, and `web` does not touch `infra`. `ArchitectureTest`
(ArchUnit) enforces this under the verify command. A violation is fixed by
inverting the dependency or extracting an interface, never by weakening the
rule (`.claude/workflow.md`).

## Key decisions

- SQLite uses a single-connection pool (one writer, avoids "database is
  locked"); the parent directory of the DB file is created at startup.
- Kotlin targets JVM 21 bytecode so CI (Temurin 21) and newer local JDKs agree.
- Feature files stay in `specs/features/`; the build copies them onto the test
  classpath for Cucumber (see `docs/testing.md`).
