Feature: Redirects keep the scheme the page was served over
  Behind a reverse proxy that ends TLS, Starve can see plain HTTP even though the
  browser is on HTTPS (when the proxy sends no X-Forwarded-Proto, or sends it
  from an address Tomcat does not trust). Every redirect then named
  "http://host/…", so a browser following one from an HTTPS page, as htmx does
  after adding a source or a group, blocked it as mixed content and the change
  only showed after a reload.

  Covers: every redirect's Location naming only the path ("/sources",
  "/login"), never a scheme or host, so the browser resolves it against the
  page it is on: a form's redirect after it saves, a signed-out request's
  redirect to the sign-in page, and signing in returning to the page asked for.
  Out of scope: X-Forwarded-For and the client address the sign-in throttle
  sees, which still need the proxy to forward it from a trusted address
  (README); redirects to another site, such as opening an item's original link,
  which stay absolute.

  Background:
    Given a fresh SQLite database file

  Scenario: A form's redirect names only the path
    Given the application is running
    When I submit the create-group form for "Music"
    Then the redirect's Location is exactly "/sources"

  Scenario: A signed-out request's redirect to sign in names only the path
    Given the application is running
    And I am not signed in
    When I request "/feed"
    Then the redirect's Location is exactly "/login"

  Scenario: Signing in returns to the page asked for by its path only
    Given the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And I have requested "/sources"
    When I log in as "rob" with password "correct-horse-1"
    Then the redirect's Location is exactly "/sources"
