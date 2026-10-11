Feature: Project skeleton
  A runnable, testable Starve application shell: a server-rendered placeholder
  home page, a health endpoint, and SQLite persistence with schema migrations.

  Covers: the home page placeholder ("Starve" plus an empty-state message),
  the /actuator/health endpoint, Flyway migrations applied to a fresh SQLite
  database on startup, persistence of the database file across an application
  restart, and 404 handling for unknown paths.
  Supersedes: nothing.
  Out of scope: all feed functionality (sources, fetching, reading) tracked in
  issue #2 onward; authentication (single-user, self-hosted); container-level
  behavior (verified by the CI docker build and manual playtest).

  Background:
    Given a fresh SQLite database file

  Scenario: Home page shows the app name and an empty state
    Given the application is running
    When I request the home page "/"
    Then the response status is 200
    And the page contains "Starve"
    And the page contains "No sources yet"

  Scenario: Health endpoint reports UP
    Given the application is running
    When I request "/actuator/health"
    Then the response status is 200
    And the health status is "UP"

  Scenario: Schema migrations are applied to a fresh database
    When the application starts
    Then the database file exists
    And all schema migrations are recorded as applied

  Scenario: Data survives an application restart
    Given the application is running
    And a marker value is stored in the database
    When the application is restarted with the same database file
    Then the marker value is still present in the database
    And no schema migration is applied a second time

  Scenario: Unknown path returns 404
    Given the application is running
    When I request "/does-not-exist"
    Then the response status is 404

# Non-goals:
#   - Any feed, source, or item functionality (issue #2 onward).
#   - Authentication or multi-user support.
#   - Verifying the Docker image locally (built in CI only).
#   - A custom 404 error page.
#
# Risks:
#   - SQLite file locking when the app restarts in-process against the same
#     file; the step must fully close the first context before reopening.
#   - Tests must use a temp DB file, never ./data/starve.db.
#   - Docker volume persistence is not covered by Cucumber, only by the CI
#     docker build and manual playtest.
#
# Open questions:
#   - None.
