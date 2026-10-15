Feature: Public install and setup guide at /docs
  "/docs" is a public page that explains how to install and set up Starve:
  running it with one Docker command that builds the master branch straight
  from the GitHub repository, with Docker Compose, or from source; where the database lives,
  the first-run setup code and the admin account, adding sources and
  importing subscriptions, letting someone else sign up, and running behind
  a reverse proxy. It is a read-only page: open to anonymous visitors and
  signed-in users alike, and still reachable while no user exists (it does
  not redirect to "/setup", because it explains setup). It carries the theme
  switch, works at phone width and shows no images. The landing page "/" and
  the setup page "/setup" link to it.

  Covers: GET "/docs" for anonymous visitors, signed-in users and a fresh
  instance with no users; the key terms it must explain; the links to it from
  "/" and "/setup".
  Out of scope: anything the browser does with the page (layout, the theme
  switch, the table of contents' scrolling): covered by the human playtest.
  Versioned or per-release docs; docs inside the signed-in app's top bar.

  Background:
    Given a fresh SQLite database file

  Scenario: An anonymous visitor reads the install guide
    Given the application is running
    And I am not signed in
    When I request "/docs"
    Then the response status is 200
    And the response is a complete HTML document
    And the page contains "docker build -t starve https://github.com/SwiftFaze/Starve.git#master"
    And the page contains "docker compose up"
    And the page contains "./mvnw spring-boot:run"
    And the page contains "Starve setup code"
    And the page contains "STARVE_DB_PATH"
    And the page contains "OPML"
    And the page contains "X-Forwarded-For"
    And the page contains no images
    And the page has a theme switch

  Scenario: A signed-in user can read the guide too
    Given the application is running
    When I request "/docs"
    Then the response status is 200
    And the page contains "Starve setup code"

  Scenario: The guide is reachable before the first account exists
    Given the application is running with no users yet
    When I request "/docs"
    Then the response status is 200
    And the page contains "Starve setup code"

  Scenario: The landing page links to the guide
    Given the application is running
    And I am not signed in
    When I request "/"
    Then the response contains a link to "/docs"

  Scenario: The setup page links to the guide
    Given the application is running with no users yet
    When I request "/setup"
    Then the response contains a link to "/docs"
