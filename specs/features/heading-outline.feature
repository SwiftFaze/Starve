Feature: One heading outline on every page
  Every page names itself with exactly one h1, a sheet title never just
  repeats it, and an open post's title owns the post's own headings, so a
  screen reader's heading navigation reads the page once and in order (#100).

  Covers: the obi name as the one h1 on every signed-in page, the access pages
  and the landing, including "/feed", where the wordmark is now a plain link
  (it was the h1, with the obi name an h2); sheet titles that only repeated the
  obi name made distinct ("Sign in to Starve", "Create an account", "Manage
  this Starve"); a post's body headings, in the reader and in the detail view,
  re-levelled for display so the body's top heading is an h3 under the post's
  h2 title, keeping their distance and stopping at h6 (the stored body is
  never changed).
  Supersedes: nothing; heading sizes come from classes, so nothing looks
  different.
  Out of scope: visual changes; per-row item headings and the skip link (#99).

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario Outline: A signed-in page has one h1, its obi name, and no sheet title repeating it
    When I request "<page>"
    Then the page has exactly one h1, reading "<name>"
    And no other heading on the page repeats "<name>"

    Examples:
      | page     | name     |
      | /feed    | All      |
      | /sources | Sources  |
      | /settings | Settings |
      | /account | Account  |
      | /import  | Import subscriptions |

  Scenario: The timeline's wordmark is a plain link, not a heading
    When I request "/feed"
    Then the wordmark is not a heading

  Scenario: The sign-in page names itself once
    Given I am not signed in
    When I request "/login"
    Then the page has exactly one h1, reading "Sign in"
    And no other heading on the page repeats "Sign in"

  Scenario: The sign-up page names itself once
    Given signups are on
    And I am not signed in
    When I request "/signup"
    Then the page has exactly one h1, reading "Sign up"
    And no other heading on the page repeats "Sign up"

  Scenario: An open post's headings sit under its title
    Given the source "Blog" has an item "Post" with the link "https://blog.test/post" and the stored body "<h2>Part one</h2><p>a</p><h3>Detail</h3><p>b</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader's body has the headings "h3 Part one, h4 Detail"
    And the stored body of the item of "Blog" contains "<h2>Part one</h2>"
