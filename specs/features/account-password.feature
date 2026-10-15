Feature: The Account page, where any signed-in user changes their own password
  Every signed-in user, admin or regular, has an Account page at "GET /account",
  linked as "Account" from the top bar on every signed-in page (the same
  "bar-actions" fragment as "Settings" and "Sign out", so it is present in the
  bar when htmx swaps it out of band). It has a "Change password" form
  posting "/account/password" with the CSRF token, working without JavaScript:
  a current password and a new password, no confirmation field (as signup).

  Rules: the new password follows the same rules and messages as setup and
  signup (at least 10 characters, at most 72 bytes UTF-8). A wrong current
  password re-renders the form (status 200) with "Current password is
  incorrect" and changes nothing. Success stores a new BCrypt hash, keeps the
  current session signed in and redirects to "/account?changed", which says
  "Password changed". The old password stops working at once and the new one
  logs in. The current-password check is throttled with the shared
  FailureThrottle: 5 wrong attempts block the user at that client address for
  15 minutes under a key of its own ("password|<username>|<address>"), so it
  never blocks or is blocked by the login throttle; while blocked even the
  right password is refused with "Too many failed attempts. Try again later.";
  a success resets the count. Rule failures on the new password do not count.

  Remember me: the hash-based remember-me cookie is signed over the password
  hash, so changing the password invalidates every remember-me cookie of that
  user (no new code: this pins the behaviour). Only remember-me is
  invalidated; the user's other live sessions are not ended (no session
  registry), and the changer's own session continues.

  Access: "/account" and "/account/password" need any signed-in user; an
  anonymous visitor is sent to "/login"; the POST needs a CSRF token (403
  without).

  Supersedes: nothing; no existing scenario changes. The following existing
  descriptions mention work now delivered and may be reworded by the coder
  (no scenario edits): self-signup.feature Out of scope "changing one's own
  password (#45)", per-user-data.feature Out of scope "user deletion UI and
  account pages (#45)" (user deletion is user-management.feature, the Account
  page is this file).
  Out of scope: admin reset and deletion of users (user-management.feature);
  email reset, role changes, disabling accounts, forced change on next login;
  ending other live sessions; browser look and layout (human playtest).

  Test harness (new steps, implemented by the coder): "When I change my
  password from {string} to {string}" posts the Change password form as the
  signed-in user with a valid CSRF token; "I submit the password change from
  {string} to {string} {int} times" repeats a failing attempt. "Then the
  Account page shows the error {string}" asserts the re-rendered form's
  message. "Then the Account page has a form that posts {string} with the CSRF
  token asking for a current password and a new password and no
  confirmation" asserts the structure. "Then the top bar has a {string} link
  to {string}" and "the response contains the top bar with the {string} link"
  are reused for "Account" (the latter takes the link text; both already
  exist). "Given {string} has logged in with Remember me in a second browser"
  creates a client that holds only that user's remember-me cookie (no
  session); "Then the second browser requesting {string} now returns status
  {int}" and "... redirects to {string}" request with that client. Existing
  steps reused: "the application is running", "the user {string} exists" (non-admin,
  password "correct-horse-1"), "I sign in as {string}", "I am not signed in",
  "I log in as {string} with password {string}" (optionally "ticking"), "I
  request {string}", "I request the page {string} via htmx", "the response
  redirects to", "the response status is", "the page contains", "requesting
  {string} redirects to|now returns status", "the browser session ends",
  "15 minutes pass", "the stored password of {string} is a BCrypt hash, not
  {string}", "the login form is shown again with the error {string}".

  Background:
    Given a fresh SQLite database file
    And the application is running
    And the user "friend" exists

  # ---------- Reaching the page ----------

  Scenario: Every signed-in user has an Account link in the top bar
    When I request "/feed"
    Then the top bar has a "Account" link to "/account"
    And the top bar has a "Settings" link to "/settings"
    When I sign in as "friend"
    And I request "/feed"
    Then the top bar has a "Account" link to "/account"
    And the top bar has no "Settings" link

  Scenario Outline: The top bar carries the Account link on every signed-in page
    When I sign in as "friend"
    And I request "<page>"
    Then the top bar has a "Account" link to "/account"

    Examples:
      | page     |
      | /feed    |
      | /sources |
      | /import  |
      | /account |

  Scenario: The Account link survives the out-of-band bar swap
    When I sign in as "friend"
    And I request the page "/sources" via htmx
    Then the response contains the top bar with the "Account" link
    When I request the page "/feed" via htmx
    Then the response contains the top bar with the "Account" link

  Scenario: The Account page shows the change-password form and keeps the sign-out control
    When I sign in as "friend"
    And I request "/account"
    Then the response status is 200
    And the page contains "friend"
    And the Account page has a form that posts "/account/password" with the CSRF token asking for a current password and a new password and no confirmation
    And the top bar has a "Sign out" button that posts "/logout" with the CSRF token

  Scenario: The Account page needs a signed-in user
    When I am not signed in
    Then requesting "/account" redirects to "/login"
    And posting "/account/password" with the CSRF token also redirects to "/login"

  # ---------- Changing the password ----------

  Scenario: A user changes their password
    When I sign in as "friend"
    And I change my password from "correct-horse-1" to "battery-staple-2"
    Then the response redirects to "/account?changed"
    And the stored password of "friend" is a BCrypt hash, not "battery-staple-2"
    When I request "/account?changed"
    Then the page contains "Password changed"
    And requesting "/feed" now returns status 200

  Scenario: The new password logs in and the old one no longer does
    When I sign in as "friend"
    And I change my password from "correct-horse-1" to "battery-staple-2"
    And I am not signed in
    And I log in as "friend" with password "correct-horse-1"
    Then the login form is shown again with the error "Invalid username or password"
    When I log in as "friend" with password "battery-staple-2"
    Then requesting "/feed" now returns status 200

  Scenario: The admin changes their own password the same way
    When I change my password from "correct-horse-1" to "battery-staple-2"
    Then the response redirects to "/account?changed"
    When I am not signed in
    And I log in as "owner" with password "battery-staple-2"
    Then requesting "/feed" now returns status 200

  Scenario: A wrong current password changes nothing
    When I sign in as "friend"
    And I change my password from "not-my-password" to "battery-staple-2"
    Then the response status is 200
    And the Account page shows the error "Current password is incorrect"
    When I am not signed in
    And I log in as "friend" with password "correct-horse-1"
    Then requesting "/feed" now returns status 200

  Scenario Outline: A new password that breaks the rules is refused
    When I sign in as "friend"
    And I change my password from "correct-horse-1" to "<new>"
    Then the response status is 200
    And the Account page shows the error "<error>"
    When I am not signed in
    And I log in as "friend" with password "correct-horse-1"
    Then requesting "/feed" now returns status 200

    Examples:
      | new                                                                                           | error                                   |
      | short-1                                                                                       | Password must be at least 10 characters |
      | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | Password is too long                    |

  Scenario: A new password of exactly 10 characters is accepted
    When I sign in as "friend"
    And I change my password from "correct-horse-1" to "abcdefghij"
    Then the response redirects to "/account?changed"

  Scenario: The change needs the CSRF token
    When I sign in as "friend"
    And I post "/account/password" without its CSRF token
    Then the response status is 403

  # ---------- Throttle ----------

  Scenario: Repeated wrong current passwords are throttled
    When I sign in as "friend"
    And I submit the password change from "wrong-guess-1" to "battery-staple-2" 5 times
    And I change my password from "correct-horse-1" to "battery-staple-2"
    Then the response status is 200
    And the Account page shows the error "Too many failed attempts. Try again later."
    When 15 minutes pass
    And I change my password from "correct-horse-1" to "battery-staple-2"
    Then the response redirects to "/account?changed"

  Scenario: The password throttle and the login throttle are separate
    When I sign in as "friend"
    And I submit the password change from "wrong-guess-1" to "battery-staple-2" 5 times
    And I am not signed in
    And I log in as "friend" with password "correct-horse-1"
    Then requesting "/feed" now returns status 200

  # ---------- Remember me ----------

  Scenario: Changing the password ends the user's remember-me cookie
    When I sign in as "friend"
    And I log out
    And I log in as "friend" with password "correct-horse-1" ticking "Remember me"
    And I change my password from "correct-horse-1" to "battery-staple-2"
    Then requesting "/feed" now returns status 200
    When the browser session ends
    Then requesting "/feed" redirects to "/login"

  Scenario: Another browser's remember-me cookie stops working after a password change
    Given "friend" has logged in with Remember me in a second browser
    Then the second browser requesting "/feed" now returns status 200
    When I sign in as "friend"
    And I change my password from "correct-horse-1" to "battery-staple-2"
    Then the second browser requesting "/feed" redirects to "/login"

  # Non-goals:
  #   - Ending the user's other live sessions on a password change
  #     (auto-decided: only remember-me is invalidated).
  #   - Reissuing the changer's remember-me cookie after the change.
  #   - Email reset, forced change on next login, password strength meters.
  #   - Exact copy beyond "Account", "Password changed", "Current password is
  #     incorrect" and the existing rule/throttle messages; browser look and
  #     the 375px layout: human playtest.
  #
  # Risks:
  #   - The remember-me invalidation relies on Spring's token signature
  #     covering the password hash. A change to how the cookie is built (for
  #     example a persistent token repository) would silently break it; the
  #     two remember-me scenarios guard that.
  #   - The throttle key must differ from "login|..." and must be per
  #     client address as well as username; the current password must be
  #     checked with the BCrypt encoder, never compared as text.
  #   - The new password's 72-byte cap is in UTF-8 bytes, not characters
  #     (BCrypt truncates silently beyond it); reuse AccountRules.
  #   - Case-insensitive usernames: the key lower-cases the username as login
  #     does.
  #   - Other live sessions of the same user keep working until they end.
  #
  # Open questions: none (resolved in specs/intent/account-admin.md
  # Clarifications; auto-decided, REAL: a password change does not end other
  # live sessions, only remember-me).
