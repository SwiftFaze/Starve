Feature: The admin manages users on Settings: list, reset a password, delete
  With no email, a forgotten password can only be fixed by the admin, and a
  friend can only be removed by the admin. The admin-only Settings page
  ("GET /settings", self-signup.feature) gains a "Users" list and two actions.
  Everything under "/settings/users/**" is admin-only on the server (a regular
  user gets 403 on every GET and POST, an anonymous visitor is sent to
  "/login"), every POST needs the CSRF token (403 without), and every action
  works without JavaScript.

  User list: one row per account, oldest first: the username, an "Admin"
  badge on admins (only the first-run user is one), and the "Created" date as
  YYYY-MM-DD (UTC). The signed-in admin's own row is marked "[you]" (square brackets, #38) and has no
  action controls. Every other row has a "Reset password" form posting
  "/settings/users/{id}/reset" and a "Delete" link to
  "/settings/users/{id}/delete".

  Reset: "POST /settings/users/{id}/reset" gives that user a new random
  temporary password: 16 characters from the setup-code alphabet (no
  look-alikes 0, O, 1, I, L) in four hyphen-separated groups, for example
  "K7QM-2XNP-W4RD-9HTC" (so it satisfies the normal password rules). It is
  stored as a BCrypt hash, never logged, and answered directly in that
  response (status 200, "Cache-Control: no-store", no redirect, nothing kept in
  the session or the URL) with a note that it is shown once and a link back
  to "/settings". It appears nowhere else; every reset makes a new one. The old
  password stops working, the temporary one logs in, and every remember-me
  cookie of that user stops working (the cookie is signed over the password
  hash; no new code, this pins the behaviour). Reset asks no confirmation.
  The admin cannot reset their own password here (400, nothing changes); the
  Account page (account-password.feature) is where they change it. Other live
  sessions of the reset user are not ended (auto-decided; only remember-me).
  The user is not forced to change it (out of scope); they simply can on
  "/account".

  Delete: "GET /settings/users/{id}/delete" is a confirmation page that names
  the user, says their account and all their sources, groups and items will be
  permanently deleted, and offers a "Delete permanently" form posting
  "/settings/users/{id}/delete" and a "Cancel" link to "/settings"; the GET
  deletes nothing. The POST (also valid without having seen the page, the
  CSRF token being the guard) deletes the user; their sources, groups, items
  and search entries go with them by ON DELETE CASCADE (per-user-data.feature)
  and nobody else's data changes. It redirects to "/settings", which says
  "Deleted user <name>" once. The admin cannot delete themselves: the
  confirmation page and the POST both answer 400 and delete nothing. An id
  that matches no user (including a non-numeric one) is 404 on the reset and
  on both delete requests.

  A deleted user's next request: any live session or remember-me cookie of
  that user is treated as signed out: an authenticated page redirects to
  "/login" (an htmx request is told to load "/login" as a full page), also for
  pages that do not look up the user (such as "/account"); the account no
  longer logs in. This replaces today's bare 401 for that case (no existing
  scenario asserts it; the resolver's 401 remains a backstop).

  Superseded in part by confirm-destructive-actions.feature: a row's Reset
  password is now a link to "/settings?reset={id}", whose confirming row holds
  the reset form.

  Supersedes: nothing; no existing scenario changes. The following existing
  descriptions mention work now delivered and may be reworded by the coder
  (no scenario edits): self-signup.feature Out of scope "user list, admin
  password reset, user deletion (#45)"; per-user-data.feature Out of scope
  "user deletion UI and account pages (#45)". The "Settings" page copy in
  settings.html ("Only admins see this page") is free to change.
  Out of scope: email reset, role changes or promoting another admin,
  disabling instead of deleting, forced change on next login, ending the other
  live sessions of a reset or deleted user beyond what is above, editing a
  username, browser look and layout (human playtest).

  Test harness (new steps, implemented by the coder): "Given the user {string}
  was created on {string}" sets that seeded user's created_at to that date
  (UTC midnight). "Then the Settings user list is" takes a table with
  columns username, admin (yes|no), you (yes|no), in display order. "Then the
  Settings user list shows {string} created on {string}". "Then the user row of
  {string} has a {string} form that posts {string} with the CSRF token" and
  "... has a {string} link to {string}" substitute "{id}" with that user's id;
  "Then the user row of {string} has no {string} control". "When I reset the
  password of {string} in Settings" posts the reset form as the signed-in
  admin and keeps the shown temporary password; "Then the page shows a
  temporary password for {string}" asserts the response (status 200, no-store,
  the format above, "shown once", a link to "/settings"); "Then the temporary
  password is not shown again" and "Then the page does not contain the
  temporary password" check a later page; "When I log in as {string} with the
  temporary password"; "When I change my password from the temporary
  password to {string}" (the Account change step, with the remembered
  password as the current one); "Then the temporary password differs from the
  previous one" compares the last two resets. "When I delete the user {string} in Settings" requests the
  confirmation page then posts its form. "Then the confirmation page names
  {string} and has a {string} form that posts {string} with the CSRF token and
  a {string} link to {string}". "When I post {string} with a CSRF token" posts
  from the current client. "When I GET|POST {string} with the id of the user
  {string}" extends the existing id step (per-user-data.feature) with
  "user". "Given {string} has logged in with Remember me in a second browser",
  "Then the second browser requesting {string} now returns status {int}" and
  "... redirects to {string}" are shared with account-password.feature. "Then
  the stored password of {string} is a BCrypt hash, not {string}" reused with
  the shown temporary password via "the stored password of {string} is a
  BCrypt hash, not the temporary password". Existing steps reused: "the
  application is running", "the user {string} exists", "I sign in as {string}",
  "the user {string} is deleted from the database", "the source {string} has an
  unread item {string} published {int} days ago", "the group {string}
  exists", "no source titled {string} exists", "no group named {string}
  exists", "the item {string} no longer exists|still exists", "the search
  index holds {int} entries", "the source {string} and the group {string}
  still exist", "I request {string}", "I request the page {string} via htmx",
  "the response redirects to", "the response status is", "the page contains",
  "the page does not contain", "the response tells htmx to load {string} as a
  full page", "requesting {string} redirects to|now returns status", "I am
  not signed in", "I log in as", "the login form is shown again with the
  error", "the user {string} does not exist", "only the user {string} exists",
  "the browser session ends", "the response does not contain".

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running
    And the user "alice" exists
    And the user "friend" exists

  # ---------- The user list ----------

  Scenario: The Settings page lists every user, oldest first
    When I request "/settings"
    Then the Settings user list is
      | username | admin | you |
      | owner    | yes   | yes |
      | alice    | no    | no  |
      | friend   | no    | no  |

  Scenario: The list shows each user's created date
    Given the user "friend" was created on "2026-03-04"
    When I request "/settings"
    Then the Settings user list shows "friend" created on "2026-03-04"

  Scenario: Other users have Reset password and Delete, the admin's own row has no actions
    When I request "/settings"
    Then the user row of "friend" has a "Reset password" link to "/settings?reset={id}"
    And the user row of "friend" has a "Delete" link to "/settings/users/{id}/delete"
    And the user row of "owner" has no "Reset password" control
    And the user row of "owner" has no "Delete" control

  Scenario: A new signup appears in the list as a regular user
    Given signups are on
    When I am not signed in
    And I sign up as "newbie" with password "correct-horse-2"
    And I sign in as "owner"
    And I request "/settings"
    Then the Settings user list is
      | username | admin | you |
      | owner    | yes   | yes |
      | alice    | no    | no  |
      | friend   | no    | no  |
      | newbie   | no    | no  |

  # ---------- Resetting a password ----------

  Scenario: Resetting a password shows a temporary password once
    When I reset the password of "friend" in Settings
    Then the page shows a temporary password for "friend"
    And the stored password of "friend" is a BCrypt hash, not the temporary password
    When I request "/settings"
    Then the temporary password is not shown again
    And the page does not contain the temporary password

  Scenario: The temporary password logs the user in and the old one does not
    When I reset the password of "friend" in Settings
    And I am not signed in
    And I log in as "friend" with password "correct-horse-1"
    Then the login form is shown again with the error "Invalid username or password"
    When I log in as "friend" with the temporary password
    Then requesting "/feed" now returns status 200

  Scenario: Every reset makes a new temporary password
    When I reset the password of "friend" in Settings
    And I reset the password of "friend" in Settings
    Then the page shows a temporary password for "friend"
    And the temporary password differs from the previous one

  Scenario: Resetting one user leaves the others and the admin alone
    When I reset the password of "friend" in Settings
    And I am not signed in
    And I log in as "alice" with password "correct-horse-1"
    Then requesting "/feed" now returns status 200
    When I am not signed in
    And I log in as "owner" with password "correct-horse-1"
    Then requesting "/feed" now returns status 200

  Scenario: Resetting a password ends the user's remember-me cookie
    Given "friend" has logged in with Remember me in a second browser
    Then the second browser requesting "/feed" now returns status 200
    When I reset the password of "friend" in Settings
    Then the second browser requesting "/feed" redirects to "/login"

  Scenario: The forgotten-password journey ends with the friend choosing their own
    When I reset the password of "friend" in Settings
    And I am not signed in
    And I log in as "friend" with the temporary password
    And I change my password from the temporary password to "my-own-password-9"
    Then the response redirects to "/account?changed"
    When I am not signed in
    And I log in as "friend" with password "my-own-password-9"
    Then requesting "/feed" now returns status 200

  Scenario: The admin cannot reset their own password here
    When I POST "/settings/users/{id}/reset" with the id of the user "owner"
    Then the response status is 400
    When I am not signed in
    And I log in as "owner" with password "correct-horse-1"
    Then requesting "/feed" now returns status 200

  Scenario: Resetting an unknown user is a 404
    When I post "/settings/users/9999/reset" with a CSRF token
    Then the response status is 404
    When I post "/settings/users/nobody/reset" with a CSRF token
    Then the response status is 404

  Scenario: Resetting needs the CSRF token
    When I post "/settings/users/2/reset" without its CSRF token
    Then the response status is 403
    When I am not signed in
    And I log in as "alice" with password "correct-horse-1"
    Then requesting "/feed" now returns status 200

  # ---------- Deleting a user ----------

  Scenario: Deleting a user needs a confirmation page
    When I GET "/settings/users/{id}/delete" with the id of the user "friend"
    Then the response status is 200
    And the confirmation page names "friend" and has a "Delete permanently" form that posts "/settings/users/{id}/delete" with the CSRF token and a "Cancel" link to "/settings"
    And the page contains "permanently deleted"
    And the user "friend" exists and is not an admin

  Scenario: Deleting a user removes them and their data and nobody else's
    Given the source "Owner Blog" has an unread item "Owner post" published 1 days ago
    And the group "Owner Group" exists
    When I sign in as "friend"
    And the source "Friend Blog" has an unread item "Friend post" published 1 days ago
    And the group "Friend Group" exists
    And I sign in as "owner"
    And I delete the user "friend" in Settings
    Then the response redirects to "/settings"
    When I request "/settings"
    Then the page contains "Deleted user friend"
    And the Settings user list is
      | username | admin | you |
      | owner    | yes   | yes |
      | alice    | no    | no  |
    And no source titled "Friend Blog" exists
    And no group named "Friend Group" exists
    And the item "Friend post" no longer exists
    And the search index holds 1 entries
    And the item "Owner post" still exists
    And the source "Owner Blog" and the group "Owner Group" still exist

  Scenario: The deletion notice is shown once
    When I delete the user "friend" in Settings
    And I request "/settings"
    And I request "/settings"
    Then the page does not contain "Deleted user friend"

  Scenario: A deleted user can no longer log in
    When I delete the user "friend" in Settings
    And I am not signed in
    And I log in as "friend" with password "correct-horse-1"
    Then the login form is shown again with the error "Invalid username or password"

  Scenario: A deleted user's live session is signed out
    When I sign in as "friend"
    And the user "friend" is deleted from the database
    Then requesting "/feed" redirects to "/login"
    And requesting "/account" redirects to "/login"
    And requesting "/sources" redirects to "/login"

  Scenario: A deleted user's htmx request is told to load the login page
    When I sign in as "friend"
    And the user "friend" is deleted from the database
    And I request the page "/feed" via htmx
    Then the response tells htmx to load "/login" as a full page

  Scenario: A deleted user's remember-me cookie stops working
    Given "friend" has logged in with Remember me in a second browser
    When I delete the user "friend" in Settings
    Then the second browser requesting "/feed" redirects to "/login"

  Scenario: The admin cannot delete themselves
    When I GET "/settings/users/{id}/delete" with the id of the user "owner"
    Then the response status is 400
    When I POST "/settings/users/{id}/delete" with the id of the user "owner"
    Then the response status is 400
    And the user "owner" exists and is an admin
    And requesting "/feed" now returns status 200

  Scenario: Deleting an unknown user is a 404
    When I request "/settings/users/9999/delete"
    Then the response status is 404
    When I post "/settings/users/9999/delete" with a CSRF token
    Then the response status is 404
    When I post "/settings/users/nobody/delete" with a CSRF token
    Then the response status is 404
    And the user "friend" exists and is not an admin

  Scenario: Deleting needs the CSRF token
    When I post "/settings/users/2/delete" without its CSRF token
    Then the response status is 403
    And the user "alice" exists and is not an admin

  # ---------- Access ----------

  Scenario Outline: A regular user is refused every user-management request
    When I sign in as "friend"
    And I request "<path>"
    Then the response status is 403

    Examples:
      | path                       |
      | /settings/users/1/delete   |
      | /settings/users/2/delete   |
      | /settings/users/9999/delete |

  Scenario Outline: A regular user cannot reset or delete anyone
    When I sign in as "friend"
    And I post "<path>" with a CSRF token
    Then the response status is 403
    And the user "alice" exists and is not an admin
    And the user "owner" exists and is an admin

    Examples:
      | path                        |
      | /settings/users/1/reset     |
      | /settings/users/2/reset     |
      | /settings/users/2/delete    |
      | /settings/users/3/delete    |
      | /settings/users/9999/reset  |
      | /settings/users/9999/delete |

  Scenario: A regular user's Settings page shows no user list
    When I sign in as "friend"
    And I request "/settings"
    Then the response status is 403
    And the response does not contain "alice"

  Scenario: An anonymous visitor is sent to the login page
    When I am not signed in
    Then requesting "/settings/users/2/delete" redirects to "/login"
    And posting "/settings/users/2/reset" with the CSRF token also redirects to "/login"
    And posting "/settings/users/2/delete" with the CSRF token also redirects to "/login"

  # Non-goals:
  #   - Role changes, promoting a second admin, disabling accounts,
  #     forced password change, email reset, editing usernames.
  #   - Ending a reset or deleted user's other live sessions beyond the
  #     deleted-session sign-out above; ending the live sessions of a reset
  #     user (auto-decided: only remember-me).
  #   - Exact copy beyond "Users", "Admin", "[you]", "Created", "Reset
  #     password", "Delete", "Delete permanently", "Cancel", "Deleted user
  #     <name>" and "permanently deleted"; browser look and the 375px layout:
  #     human playtest.
  #
  # Risks:
  #   - The temporary password is a credential: it must come from
  #     SecureRandom, must not be logged, must not be redirected through the
  #     URL or session (a flash attribute would store it), and the response
  #     must not be cached ("Cache-Control: no-store").
  #   - The "admin cannot delete themselves" check must compare the account,
  #     not trust the hidden control; the 400 must come before any delete.
  #     The last remaining admin therefore can never be removed.
  #   - Deleting must cascade through users -> sources -> items and the
  #     item_search rows (V14 triggers); a failure would leave orphan search
  #     entries visible to nobody but breaking the "search index holds N
  #     entries" count. SQLite needs foreign_keys=ON on the connection.
  #   - A deleted user's session is tied to the username: if a new account
  #     takes that name while the old session is live, the session acts as
  #     the new account (accepted at this scale).
  #   - Making deleted-user sessions end for pages that do not call
  #     @CurrentUserId needs a filter or a check in the security chain; the
  #     existing 401 from the resolver is only a backstop and leaks no data.
  #   - "/settings/users/**" is covered by the existing hasRole("ADMIN")
  #     matcher on "/settings/**"; keep it that way (no new public paths).
  #   - The ids in the scenarios assume seeding order (owner 1, alice 2,
  #     friend 3); the coder may resolve them by name instead.
  #
  # Open questions: none (resolved in specs/intent/account-admin.md
  # Clarifications; auto-decided, REAL: the temporary-password display, no
  # self-reset by the admin, and that a reset or password change does not
  # end other live sessions).
