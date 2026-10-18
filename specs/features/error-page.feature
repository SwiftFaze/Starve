Feature: A styled error page
  A request the app answers with an error page (an unknown address, an id that
  is not the visitor's, a page for admins only, an expired form, a server
  failure) gets a page in the app's own look instead of Spring Boot's
  Whitelabel page (#110).

  Covers: templates/error.html for every HTML error response; the bar for the
  visitor's state (the app bar signed in, the public bar signed out); an obi
  naming the status code; a sheet head naming the problem in plain words
  ("Nothing here" for 404, "This form expired" for a refused post, which is
  almost always a stale CSRF token, "Admins only" for a page refused to a
  signed-in user, "Not allowed" for any other 403, "Something
  broke on this server" for 5xx, "That didn't work" for any other status); one
  line of help; a filled link back, to the Timeline signed in or Home signed
  out; no stack trace, exception class or Whitelabel wording; a 404 for another
  user's id reading exactly like one for an id that never existed.
  Supersedes: nothing; the status codes the other features assert are
  unchanged.
  Out of scope: htmx fragment errors, which never show a page; non-HTML
  clients, which keep Spring Boot's JSON error body; the login redirect of
  #109; the theme, checked by eye.

  Background:
    Given a fresh SQLite database file
    And the application is running

  Scenario: An unknown address shows the styled 404 page with a way back to the timeline
    When I open "/no-such-page" in a browser
    Then the response status is 404
    And the error page names the status "404" and says "Nothing here"
    And the error page links back to "Timeline" at "/feed"
    And the error page has the signed-in bar
    And the error page shows no framework details

  Scenario: Another user's item reads like an item that never existed
    Given the user "friend" exists
    And the source "Daily News" has an unread playable item "Track" published 1 days ago
    When I sign in as "friend"
    And I open the detail view of the item "Track" in a browser
    Then the response status is 404
    And the error page names the status "404" and says "Nothing here"
    And the error page matches the one for "/items/999999/detail"

  Scenario: A page for admins only says so to a signed-in user
    Given the user "friend" exists
    When I sign in as "friend"
    And I open "/settings" in a browser
    Then the response status is 403
    And the error page names the status "403" and says "Admins only"
    And the error page links back to "Timeline" at "/feed"

  Scenario: A form posted without its CSRF token says the form expired
    When I post "/account/password" without its CSRF token in a browser
    Then the response status is 403
    And the error page names the status "403" and says "This form expired"

  Scenario: Signed out, the error page has the public bar and a way home
    Given I am not signed in
    When I open "/setup" in a browser
    Then the response status is 404
    And the error page names the status "404" and says "Nothing here"
    And the error page links back to "Home" at "/"
    And the error page has the public bar
