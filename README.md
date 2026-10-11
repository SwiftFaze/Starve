# Starve

A self-hosted, single-user personal feed aggregator (RSS-first: YouTube,
SoundCloud, news). Built with Kotlin, Spring Boot, Thymeleaf/htmx and SQLite.
Feed features are tracked in the GitHub issues. Today you can subscribe to
RSS/Atom feeds; Starve fetches them in the background and stores their items.

## Run locally

Needs JDK 21+.

```
./mvnw spring-boot:run
```

Open <http://localhost:8080>. The database is `./data/starve.db` (override with
`STARVE_DB_PATH` or `--starve.db.path`).

## Subscribing to feeds

Open **Sources** (<http://localhost:8080/sources>) and paste a feed URL
(`http` or `https`). Starve fetches it once to confirm it is a valid RSS or Atom
feed before saving, and rejects duplicates. Each source shows its title, when it
was last fetched and its last error, if any; unsubscribe from the same page.

Subscriptions are polled in the background with conditional GET (ETag /
Last-Modified), so unchanged feeds cost almost nothing. One failing feed never
stops the others. Items older than the retention age (measured from when Starve
first saw them) are deleted daily.

## Configuration

Set as Spring properties (`--starve.poll.interval=PT15M`) or environment
variables (`STARVE_POLL_INTERVAL=PT15M`). Durations use ISO-8601.

| Property | Default | Meaning |
|---|---|---|
| `starve.db.path` | `./data/starve.db` | SQLite file |
| `starve.poll.interval` | `PT30M` | delay between polls of all sources |
| `starve.fetch.timeout` | `PT10S` | limit for one whole feed fetch, body included |
| `starve.retention.max-age` | `P90D` | items first seen longer ago are purged |
| `starve.retention.interval` | `PT24H` | delay between retention purges |
| `starve.scheduling.enabled` | `true` | `false` turns off background polling and purging |

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
