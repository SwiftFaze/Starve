Feature: Self-service signup, switched on and off by an admin in Settings
  The owner can let a friend register without handling their password, then
  close the door again. Accounts are admin or regular: the first-run setup
  user is the admin; everyone who signs up is a regular user, never an admin
  (no screen promotes anyone).

  Settings: "GET /settings" is an admin-only page, linked as "Settings" from
  the top bar for admins only (and present in the bar when htmx swaps it out
  of band, like "Sign out"). It has an "Allow new signups" switch: a form
  posting "/settings/signups" with the CSRF token, working without
  JavaScript, redirecting back to "/settings". The state is stored in
  app_metadata (no migration; no row means off), is OFF by default, and
  after a restart is whatever was last set. A regular user gets 403 on both
  GET and POST; an anonymous visitor is sent to "/login".

  Signup: "GET /signup" is public once a user exists. While signups are on it
  shows a form with a username and a password (no confirmation field) and a
  link to "/login"; "POST /signup" (CSRF-protected) applies the same rules and
  messages as setup (username 3-32 of letters, digits ".", "_", "-"; password
  at least 10 characters and at most 72 bytes UTF-8; username unique
  case-insensitively, else "That username is already taken"), creates a
  regular user, signs them in and redirects to "/feed", which is empty
  (per-user data, #43). While signups are off, "GET /signup" shows "Signups
  are closed" (status 200, no form, a link to "/login") and "POST /signup"
  returns 403 and creates nothing. The switch is read on every request, so
  closing takes effect at once; existing users and sessions are unaffected.
  The "Sign up" links on the landing page "/" and on "/login" exist only
  while signups are on. A signed-in visitor to "/signup" is redirected to
  "/feed" whether signups are on or off; while no user exists it redirects to
  "/setup" like every route. Failed signup attempts (validation or taken
  username) are throttled per client IP like setup codes: 5 failures block
  that IP for 15 minutes ("Too many failed attempts. Try again later."),
  successes do not count, state is in memory.

  Covers: the admin/regular distinction in practice; "/settings" and
  "/settings/signups"; the "Allow new signups" setting and its default; the
  "Settings" top-bar link and its htmx survival; "/signup" in both states; the
  conditional "Sign up" links on "/" and "/login"; signup throttling; CSRF on
  both forms.
  Supersedes (the coder must update these existing files):
    - public-home-page.feature:
        * description: "shows no screenshots and no sign-up link" becomes "no
          screenshots; a 'Sign up' link to '/signup' only while signups are
          on (self-signup.feature)"; "Out of scope: the 'Sign up' link (#44)"
          is dropped; the Non-goals line "A 'Sign up' link or any
          registration (#44)" is dropped.
        * scenario "The landing page has no screenshots, no sign-up link and
          no sign-out control": rename to "The landing page has no
          screenshots and no sign-out control" and delete its step `And the
          page does not contain "Sign up"` (the closed-state absence is now
          asserted in "Sign up links are hidden while signups are off" here,
          since "Given the application is running" leaves signups off).
    - accounts-login.feature description: remove "signup (#44)" from the
      Out of scope list and add "/signup" to the public routes sentence.
    - per-user-data.feature: the description's "(signup is #44)" and its
      Out of scope "signup (#44)" may be reworded to "signup is
      self-signup.feature"; no scenario changes (seeding keeps using the
      user repository).
  Out of scope: (delivered elsewhere: user list, admin password reset and deletion in user-management.feature, changing
  one's own password in account-password.feature); email verification, invitations, per-signup
  approval; promoting a user to admin. Browser look and feel of the Settings
  switch and the 375px layout are settled at the human playtest; scenarios pin
  structure and key terms only.

  Test harness (new steps, implemented by the coder): "Given the application
  is running" is setup done for "owner" (admin), the client signed in, and
  signups OFF. "Given signups are on|off" sets the app_metadata value
  directly. "When I turn signups on|off in Settings" drives the real Settings
  form as the signed-in admin. "When I sign up as {string} with password
  {string}" posts the signup form from the current client (anonymous in these
  scenarios) with a valid CSRF token; "the client {string} signs up as ...
  with password ..." does so from that client IP; "I submit signup as ...
  with password ... {int} times" repeats a failing attempt. Existing steps
  reused: "I am not signed in", "I log out", "I log in as", "I sign in as
  {string}" and "the user {string} exists" (non-admin seeded user,
  per-user-data), "I request {string}", "I request the page {string} via
  htmx", "the response redirects to", "the response status is", "the page
  contains|does not contain", "the response contains a link to", "the landing
  page has a {string} link to {string}", "the wordmark on {string} links to
  {string}", "the page has no {string} control", "the application is
  restarted with the same database file", "only the user {string} exists",
  "the user {string} exists and is an admin", "15 minutes pass".

  Background:
    Given a fresh SQLite database file

  # ---------- The switch ----------

  Scenario: Signups are off by default
    Given the application is running
    Then the Settings page shows the "Allow new signups" switch off
    When I am not signed in
    And I request "/signup"
    Then the page contains "Signups are closed"

  Scenario: An admin turns signups on from Settings
    Given the application is running
    When I request "/settings"
    Then the response status is 200
    And the response is a complete HTML document
    And the Settings page shows the "Allow new signups" switch off
    And the "Allow new signups" form posts "/settings/signups" with the CSRF token
    When I turn signups on in Settings
    Then the response redirects to "/settings"
    And the Settings page shows the "Allow new signups" switch on

  Scenario: An admin turns signups back off
    Given the application is running
    And signups are on
    When I turn signups off in Settings
    Then the Settings page shows the "Allow new signups" switch off

  Scenario: The setting survives a restart
    Given the application is running
    And I turn signups on in Settings
    When the application is restarted with the same database file
    And I sign in as "owner"
    Then the Settings page shows the "Allow new signups" switch on

  Scenario: The switch needs the CSRF token
    Given the application is running
    When I post "/settings/signups" without its CSRF token
    Then the response status is 403
    And the Settings page shows the "Allow new signups" switch off

  # ---------- Who can reach Settings ----------

  Scenario: A regular user cannot reach Settings
    Given the application is running
    And the user "friend" exists
    When I sign in as "friend"
    And I request "/settings"
    Then the response status is 403
    And posting "/settings/signups" with the CSRF token also returns status 403
    And signups are still off

  Scenario: An anonymous visitor to Settings is sent to login
    Given the application is running
    And I am not signed in
    Then requesting "/settings" redirects to "/login"
    And posting "/settings/signups" with the CSRF token also redirects to "/login"

  Scenario: The Settings link is in the top bar for admins only
    Given the application is running
    And the user "friend" exists
    When I request "/feed"
    Then the top bar has a "Settings" link to "/settings"
    When I sign in as "friend"
    And I request "/feed"
    Then the top bar has no "Settings" link

  Scenario Outline: The top bar carries the Settings link on every signed-in page
    Given the application is running
    When I request "<page>"
    Then the top bar has a "Settings" link to "/settings"

    Examples:
      | page      |
      | /feed     |
      | /sources  |
      | /import   |
      | /settings |

  Scenario: The Settings link survives the out-of-band bar swap
    Given the application is running
    When I request the page "/sources" via htmx
    Then the response contains the top bar with the "Settings" link
    When I request the page "/feed" via htmx
    Then the response contains the top bar with the "Settings" link

  Scenario: The Settings page keeps the sign-out control
    Given the application is running
    When I request "/settings"
    Then the top bar has a "Sign out" button that posts "/logout" with the CSRF token

  # ---------- Sign up links ----------

  Scenario: Sign up links appear on the landing and login pages while signups are on
    Given the application is running
    And signups are on
    And I am not signed in
    Then the landing page has a "Sign up" link to "/signup"
    When I request "/login"
    Then the response contains a link to "/signup"

  Scenario: Sign up links are hidden while signups are off
    Given the application is running
    And I am not signed in
    When I request "/"
    Then the page does not contain "Sign up"
    When I request "/login"
    Then the page does not contain "Sign up"

  Scenario: The links disappear again when signups are turned off
    Given the application is running
    And I turn signups on in Settings
    And I turn signups off in Settings
    When I am not signed in
    And I request "/"
    Then the page does not contain "Sign up"

  # ---------- Signing up ----------

  Scenario: The signup page shows a form while signups are on
    Given the application is running
    And signups are on
    And I am not signed in
    When I request "/signup"
    Then the response status is 200
    And the response is a complete HTML document
    And the signup form asks for a username and a password and posts "/signup" with the CSRF token
    And the signup form has no password confirmation field
    And the response contains a link to "/login"
    And the page has a theme switch
    And the page has no sign-out control
    And the wordmark on "/signup" links to "/"

  Scenario: Signing up creates a regular user, signs them in and shows an empty feed
    Given the application is running
    And signups are on
    And I am not signed in
    When I sign up as "friend" with password "correct-horse-2"
    Then the response redirects to "/feed"
    And the user "friend" exists and is not an admin
    And the stored password of "friend" is a BCrypt hash, not "correct-horse-2"
    When I request "/feed"
    Then the response status is 200
    And the page contains "No sources yet"
    And the top bar has no "Settings" link
    And requesting "/settings" returns status 403

  Scenario: A new user sees none of the owner's data
    Given the application is running
    And the source "Daily" has an unread item "Owner post" published 1 days ago
    And signups are on
    And I am not signed in
    When I sign up as "friend" with password "correct-horse-2"
    And I request "/feed?show=all"
    Then the timeline does not list "Owner post"

  Scenario: The new user can log in again later
    Given the application is running
    And signups are on
    And I am not signed in
    And I sign up as "friend" with password "correct-horse-2"
    When I log out
    And I log in as "friend" with password "correct-horse-2"
    Then the response redirects to "/feed"

  Scenario: Signing up never makes an admin, however many sign up
    Given the application is running
    And signups are on
    And I am not signed in
    When I sign up as "friend" with password "correct-horse-2"
    And I log out
    And I sign up as "pal" with password "correct-horse-3"
    Then the user "owner" exists and is an admin
    And the user "friend" exists and is not an admin
    And the user "pal" exists and is not an admin

  Scenario Outline: Boundary usernames and passwords are accepted at signup
    Given the application is running
    And signups are on
    And I am not signed in
    When I sign up as "<username>" with password "<password>"
    Then the response redirects to "/feed"
    And the user "<username>" exists and is not an admin

    Examples:
      | username                         | password                                                                 |
      | fri                              | 1234567890                                                               |
      | R.o_b-42                         | correct horse battery staple                                             |
      | abcdefghijklmnopqrstuvwxyz123456 | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa |

  Scenario Outline: Signup validates its input
    Given the application is running
    And signups are on
    And I am not signed in
    When I sign up as "<username>" with password "<password>"
    Then the signup form is shown again with the error "<error>"
    And only the user "owner" exists

    Examples:
      | username                          | password                                                                    | error                                                  |
      |                                   | correct-horse-1                                                             | Username must be 3-32 letters, digits, '.', '_' or '-' |
      | fr                                | correct-horse-1                                                             | Username must be 3-32 letters, digits, '.', '_' or '-' |
      | abcdefghijklmnopqrstuvwxyz1234567 | correct-horse-1                                                             | Username must be 3-32 letters, digits, '.', '_' or '-' |
      | bad name                          | correct-horse-1                                                             | Username must be 3-32 letters, digits, '.', '_' or '-' |
      | friend                            | short-pw1                                                                   | Password must be at least 10 characters                |
      | friend                            | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | Password is too long                                   |

  Scenario: A taken username is refused, case-insensitively
    Given the application is running
    And signups are on
    And I am not signed in
    When I sign up as "OWNER" with password "correct-horse-2"
    Then the signup form is shown again with the error "That username is already taken"
    And only the user "owner" exists
    And requesting "/feed" still redirects to "/login"

  Scenario: A signup without the CSRF token is rejected
    Given the application is running
    And signups are on
    And I am not signed in
    When I post "/signup" without its CSRF token
    Then the response status is 403
    And only the user "owner" exists

  # ---------- Signups closed ----------

  Scenario: While signups are off the signup page says so
    Given the application is running
    And I am not signed in
    When I request "/signup"
    Then the response status is 200
    And the page contains "Signups are closed"
    And the page has no signup form
    And the response contains a link to "/login"

  Scenario: A post while signups are off is rejected
    Given the application is running
    And I am not signed in
    When I sign up as "friend" with password "correct-horse-2"
    Then the response status is 403
    And only the user "owner" exists
    And requesting "/feed" still redirects to "/login"

  Scenario: Closing signups takes effect at once and leaves existing users alone
    Given the application is running
    And signups are on
    And I am not signed in
    And I sign up as "friend" with password "correct-horse-2"
    And I log out
    And I log in as "owner" with password "correct-horse-1"
    And I turn signups off in Settings
    When I log out
    And I sign up as "pal" with password "correct-horse-3"
    Then the response status is 403
    And the user "pal" does not exist
    When I log in as "friend" with password "correct-horse-2"
    Then the response redirects to "/feed"

  Scenario: Closing signups does not end a session that is already signed in
    Given the application is running
    And signups are on
    And I am not signed in
    And I sign up as "friend" with password "correct-horse-2"
    And signups are off
    Then requesting "/feed" now returns status 200

  # ---------- Other visitors ----------

  Scenario Outline: A signed-in visitor to /signup is sent to the timeline
    Given the application is running
    And signups are <state>
    Then requesting "/signup" redirects to "/feed"

    Examples:
      | state |
      | on    |
      | off   |

  Scenario: While no user exists, signup redirects to setup
    Given the application is running with no users yet
    Then requesting "/signup" redirects to "/setup"

  # ---------- Throttle ----------

  Scenario: Repeated failed signups are throttled per client IP
    Given the application is running
    And signups are on
    And I am not signed in
    When I submit signup as "ab" with password "correct-horse-2" 5 times
    And I sign up as "friend" with password "correct-horse-2"
    Then the signup form is shown again with the error "Too many failed attempts. Try again later."
    And only the user "owner" exists

  Scenario: The signup block lifts after 15 minutes
    Given the application is running
    And signups are on
    And I am not signed in
    And I submit signup as "ab" with password "correct-horse-2" 5 times
    When 15 minutes pass
    And I sign up as "friend" with password "correct-horse-2"
    Then the response redirects to "/feed"

  Scenario: Another client is not blocked by someone else's failed signups
    Given the application is running
    And signups are on
    And I am not signed in
    And the client "203.0.113.7" submits signup as "ab" with password "correct-horse-2" 5 times
    When the client "198.51.100.2" signs up as "friend" with password "correct-horse-2"
    Then the response redirects to "/feed"

  Scenario: Successful signups are not counted as failures
    Given the application is running
    And signups are on
    And I am not signed in
    When I sign up as "friend1" with password "correct-horse-2"
    And I log out
    And I sign up as "friend2" with password "correct-horse-2"
    And I log out
    And I sign up as "friend3" with password "correct-horse-2"
    And I log out
    And I sign up as "friend4" with password "correct-horse-2"
    And I log out
    And I sign up as "friend5" with password "correct-horse-2"
    And I log out
    And I sign up as "friend6" with password "correct-horse-2"
    Then the response redirects to "/feed"

  # Non-goals:
  #   - User list, admin reset/delete (user-management.feature), own password change (account-password.feature).
  #   - Promotion to admin; invitations, approval queues, email checks.
  #   - Real browser behaviour of the Settings switch and the sign-up pages
  #     (look, 375px layout): human playtest.
  #   - Exact copy beyond "Allow new signups", "Signups are closed", "Sign up",
  #     "Settings" and the quoted error messages.
  #
  # Risks:
  #   - "/signup" must become public without weakening "no users yet ->
  #     /setup" (SecurityConfig PUBLIC_PATHS), while "/settings" stays
  #     authenticated and additionally admin-only (403, not a login bounce,
  #     for a signed-in regular user). Check the role on BOTH the GET and the
  #     POST; a hidden link is not access control.
  #   - Signup creates a session by hand: reuse the setup sign-in path so the
  #     session fixation protection and CSRF token rotation still apply.
  #   - The Settings link must live inside #bar with the out-of-band swap
  #     fragments, and a regular user's bar must never render it (the
  #     fragment is rendered per request from the principal).
  #   - Two concurrent signups for the same username: the unique constraint
  #     must turn the loser into the "already taken" error, not a 500.
  #   - Existing scenarios assert "the page does not contain 'Sign up'" on
  #     the landing page; leave that only where signups are off.
  #   - The throttle shares its mechanism with the setup-code one but must
  #     not share its counters.
  #
  # Open questions: none (all resolved, see specs/intent/self-signup.md
  # Clarifications; REAL auto-decision: signup failures are throttled per
  # client IP, 5 per 15 minutes).
