Feature: Accounts and login with first-run admin setup
  Every route requires a signed-in user, except "/" (the public landing page),
  "/setup", "/login", static assets and "/actuator/health". Users live in a "users" table (username unique
  case-insensitively, BCrypt password hash, admin flag).

  First run: while no user exists, every route except health and static
  assets redirects to "/setup", and each startup logs a fresh one-time code
  "Starve setup code: XXXX-XXXX". The code is kept in memory only, matched
  case-insensitively with the hyphen optional. The setup form takes the code, a
  username and a password, creates the first user as admin and signs them in.
  Once any user exists, "/setup" is 404 and no code is logged.

  Usernames: 3-32 characters of letters, digits, ".", "_" and "-". Passwords:
  at least 10 characters and at most 72 bytes UTF-8, no composition rules.

  Login: a wrong username or password gets the same generic error. 5
  consecutive failures for one username from one client IP block that pair for
  15 minutes, even with the right password. A success resets the count. Wrong
  setup codes are throttled the same way per client IP. Throttle state is in
  memory. "Remember me" sets a 30-day hash-based cookie; without it the login
  lasts only for the browser session. The remember-me key is generated once and
  kept in app_metadata, so remembered logins survive an application restart.
  "POST /logout" ends the session, deletes
  the remember-me cookie and lands on "/", the landing page. A visitor who is already
  signed in (including via the remember-me cookie) who requests "GET /login" is
  redirected to "/feed"; the login page has no sign-out control. An htmx request without a
  session is told to load "/login" as a full page (HX-Redirect) instead of
  having the login form swapped into a fragment.

  Test harness: in every other .feature file, "Given the application is
  running" now means setup is done for the user "owner" and the test client is
  signed in as them, so those scenarios keep their meaning. This file uses
  explicit steps for "no users yet" and "not signed in".

  Covers: the users table and its migration; "/setup"; "/login"; "/logout";
  remember-me; the login and setup throttles; which routes are public; htmx
  session expiry; cookie support in the acceptance-test client.
  Supersedes: the "authentication" exclusion in project-skeleton.feature and
  chronological-feed.feature, and "There is no authentication" in
  docs/architecture.md.
  Out of scope:
  per-user data (#43); signup (#44); account page, password change and
  user management (#45); email reset, OAuth, 2FA.

  # ---------- First-run setup ----------

  Scenario: With no users, every page redirects to setup
    Given a fresh SQLite database file
    And the application is running with no users yet
    When I request "/"
    Then the response redirects to "/setup"
    And requesting "/login" also redirects to "/setup"
    And requesting "/sources" also redirects to "/setup"

  Scenario: The setup code is logged only while setup is pending
    Given a fresh SQLite database file
    And the application is running with no users yet
    Then the application log contains a setup code matching "[A-Z2-9]{4}-[A-Z2-9]{4}"
    When I complete setup with the logged code as "rob" with password "correct-horse-1"
    And the application is restarted with the same database file
    Then the application log of this run contains no setup code

  Scenario: Each startup without users logs a new code and the old one stops working
    Given a fresh SQLite database file
    And the application is running with no users yet
    And I remember the logged setup code
    When the application is restarted with the same database file
    Then the application log contains a setup code different from the remembered one
    When I submit setup with the remembered code as "rob" with password "correct-horse-1"
    Then the setup form is shown again with the error "Invalid setup code"
    And no user exists

  Scenario: Completing setup creates an admin and signs them in
    Given a fresh SQLite database file
    And the application is running with no users yet
    When I complete setup with the logged code as "rob" with password "correct-horse-1"
    Then the response redirects to "/feed"
    And the user "rob" exists and is an admin
    And the stored password of "rob" is a BCrypt hash, not "correct-horse-1"
    And requesting "/feed" now returns status 200

  Scenario: The setup code is accepted in lower case and without its hyphen
    Given a fresh SQLite database file
    And the application is running with no users yet
    When I complete setup with the logged code in lower case without its hyphen as "rob" with password "correct-horse-1"
    Then the user "rob" exists and is an admin

  Scenario: Setup rejects a wrong code
    Given a fresh SQLite database file
    And the application is running with no users yet
    When I submit setup with the code "AAAA-AAAA" as "rob" with password "correct-horse-1"
    Then the response status is 200
    And the setup form is shown again with the error "Invalid setup code"
    And no user exists

  Scenario Outline: Setup validates its input
    Given a fresh SQLite database file
    And the application is running with no users yet
    When I submit setup with the logged code as "<username>" with password "<password>"
    Then the setup form is shown again with the error "<error>"
    And no user exists

    Examples:
      | username                          | password                                                                    | error                                                                   |
      |                                   | correct-horse-1                                                             | Username must be 3-32 letters, digits, '.', '_' or '-'                  |
      | ro                                | correct-horse-1                                                             | Username must be 3-32 letters, digits, '.', '_' or '-'                  |
      | abcdefghijklmnopqrstuvwxyz1234567 | correct-horse-1                                                             | Username must be 3-32 letters, digits, '.', '_' or '-'                  |
      | rob woolley                       | correct-horse-1                                                             | Username must be 3-32 letters, digits, '.', '_' or '-'                  |
      | rob                               | short-pw1                                                                   | Password must be at least 10 characters                                 |
      | rob                               | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | Password is too long                                                    |

  Scenario Outline: Boundary usernames and passwords are accepted
    Given a fresh SQLite database file
    And the application is running with no users yet
    When I complete setup with the logged code as "<username>" with password "<password>"
    Then the user "<username>" exists and is an admin

    Examples:
      | username                         | password                                                                 |
      | rob                              | 1234567890                                                               |
      | R.o_b-42                         | correct horse battery staple                                             |
      | abcdefghijklmnopqrstuvwxyz123456 | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa |

  Scenario: Repeated wrong setup codes are throttled
    Given a fresh SQLite database file
    And the application is running with no users yet
    When I submit setup with the code "AAAA-AAAA" 5 times
    And I submit setup with the logged code as "rob" with password "correct-horse-1"
    Then the setup form is shown again with the error "Too many failed attempts. Try again later."
    And no user exists

  Scenario: Setup is gone once a user exists
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    When I request "/setup"
    Then the response status is 404
    And submitting setup with any code returns status 404
    And only the user "rob" exists

  # ---------- Public and protected routes ----------

  Scenario: Health and static assets stay public
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    When I request "/actuator/health"
    Then the response status is 200
    And the health status is "UP"
    And requesting the stylesheet returns status 200

  Scenario Outline: Protected pages send an anonymous visitor to login
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    When I request "<path>"
    Then the response redirects to "/login"

    Examples:
      | path     |
      | /feed    |
      | /sources |
      | /import  |

  Scenario: An htmx request without a session reloads the whole page on login
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    When I request the page "/sources" via htmx
    Then the response tells htmx to load "/login" as a full page
    And the response does not contain the login form

  # ---------- Login ----------

  Scenario: Logging in lands on the page originally requested
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And I have requested "/sources"
    When I log in as "rob" with password "correct-horse-1"
    Then the response redirects to "/sources"
    And requesting "/sources" now returns status 200

  Scenario: Logging in without a pending page lands on the timeline
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    When I log in as "rob" with password "correct-horse-1"
    Then the response redirects to "/feed"

  Scenario: Usernames are case-insensitive at login
    Given a fresh SQLite database file
    And the application is running with the user "Rob" with password "correct-horse-1"
    And I am not signed in
    When I log in as "rOB" with password "correct-horse-1"
    Then requesting "/feed" now returns status 200

  Scenario Outline: Wrong credentials get one generic error
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    When I log in as "<username>" with password "<password>"
    Then the login form is shown again with the error "Invalid username or password"
    And requesting "/feed" still redirects to "/login"

    Examples:
      | username | password        |
      | rob      | wrong-password  |
      | nobody   | correct-horse-1 |

  # ---------- Throttle ----------

  Scenario: Five failures in a row block that client even with the right password
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    When I log in as "rob" with password "wrong-password" 5 times
    And I log in as "rob" with password "correct-horse-1"
    Then the login form is shown again with the error "Too many failed attempts. Try again later."
    And requesting "/feed" still redirects to "/login"

  Scenario: The block lifts after 15 minutes
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And I log in as "rob" with password "wrong-password" 5 times
    When 15 minutes pass
    And I log in as "rob" with password "correct-horse-1"
    Then requesting "/feed" now returns status 200

  Scenario: A success resets the failure count
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And I log in as "rob" with password "wrong-password" 4 times
    And I log in as "rob" with password "correct-horse-1"
    And I log out
    When I log in as "rob" with password "wrong-password" 4 times
    And I log in as "rob" with password "correct-horse-1"
    Then requesting "/feed" now returns status 200

  Scenario: Another client is not blocked by someone else's failures
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And the client "203.0.113.7" logs in as "rob" with password "wrong-password" 5 times
    When the client "198.51.100.2" logs in as "rob" with password "correct-horse-1"
    Then that client can request "/feed" with status 200

  # ---------- Session, remember me, logout ----------

  Scenario: Without remember me, the login ends with the browser session
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And I log in as "rob" with password "correct-horse-1"
    Then no persistent login cookie is set
    When the browser session ends
    Then requesting "/feed" redirects to "/login"

  Scenario: Remember me keeps the user signed in after the browser session ends
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And I log in as "rob" with password "correct-horse-1" ticking "Remember me"
    Then a persistent login cookie is set that expires in 30 days
    When the browser session ends
    Then requesting "/feed" now returns status 200

  Scenario: Remember me keeps the user signed in across an application restart
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And I log in as "rob" with password "correct-horse-1" ticking "Remember me"
    When the browser session ends
    And the application is restarted with the same database file
    Then requesting "/feed" now returns status 200

  Scenario: Logout ends the session and forgets remember me
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And I log in as "rob" with password "correct-horse-1" ticking "Remember me"
    When I log out
    Then the response redirects to "/"
    And the persistent login cookie is deleted
    And requesting "/feed" redirects to "/login"

  Scenario: A signed-in visitor to the login page is sent to the timeline
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    Then requesting "/login" redirects to "/feed"

  Scenario: A visitor restored by remember me is sent from the login page to the timeline
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And I log in as "rob" with password "correct-horse-1" ticking "Remember me"
    When the browser session ends
    Then requesting "/login" redirects to "/feed"

  Scenario: A form post without a CSRF token is rejected
    Given a fresh SQLite database file
    And the application is running
    When I post the "Mark all read" form without its CSRF token
    Then the response status is 403

  Scenario: The login and setup pages follow the app's theme
    Given a fresh SQLite database file
    And the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    When I request "/login"
    Then the response status is 200
    And the response is a complete HTML document
    And the page carries the theme switch

  # Non-goals:
  #   - (Done in #42: top-bar logout control and the timeline at /feed.)
  #   - Any per-user scoping of sources/items (#43): every signed-in user still
  #     sees the one shared dataset in this issue (only one user can exist anyway).
  #   - Throttle persistence across restarts; distributed/multi-instance throttling.
  #
  # Risks:
  #   - CSRF on by default touches every existing POST (forms and htmx posts):
  #     all templates need the token, htmx needs it as a header, and the
  #     acceptance-test client must send it. Easy to miss one form; the
  #     playtest should exercise add source, import, mark read, mark all read.
  #   - Spring's saved-request cache must not save htmx fragment URLs, or a
  #     login after an htmx bounce lands on a bare fragment.
  #   - Behind a reverse proxy on a public address (not private ranges), Tomcat
  #     will not trust X-Forwarded-For, so every client shares the proxy's IP
  #     and the throttle degrades to per-username. Document in README.
  #   - Two concurrent setup submissions: the second must fail (unique
  #     constraint / "users exist" check in one transaction), never create a
  #     second admin.
  #   - Flyway version: take the next free number after rebasing on develop
  #     (V8 and V9 are claimed by milestone 2 branches).
  #
  # Open questions: none (CSRF on, approved 2026-10-14).
