Feature: Public home page presenting Starve, with the timeline at /feed
  "/" is a public landing page for anonymous visitors: what Starve is (a
  short pitch), a few highlights (feeds, YouTube and SoundCloud playback,
  import from YouTube Takeout or OPML), and a "Sign in" link to "/login". It
  carries the theme switch and works at phone width, and shows no screenshots
  and no sign-up link. A signed-in visitor to "/" is redirected to "/feed",
  keeping any query string, so old timeline URLs keep working. The timeline,
  with all its views and URLs, now lives at "/feed": the group, Favourites,
  Later, search and "show=all" parameters and the "after" paging fragment
  are unchanged apart from the path ("/feed", "/feed?favourites=1",
  "/feed?later=1", "/feed?q=...", "/feed?group=<id>", "/feed?show=all",
  "/feed?after=..."). Every link, htmx request, form redirect and wordmark the
  app generates for the timeline points at "/feed". While no user exists,
  "/" still redirects to "/setup".

  Signed-in pages with the top bar (Timeline, Sources, Import) show a "Sign
  out" button in the bar: a form that posts "/logout" with the CSRF token,
  working without JavaScript and present in the bar whenever htmx swaps it
  out-of-band. Signing out lands on "/" (the landing page). After login or
  first-run setup, and for a signed-in visitor to "/login", the default
  destination is "/feed" (a login that resumes a pending page still lands
  there). On anonymous pages (landing, login, setup) the wordmark links to
  "/" and there is no sign-out control.

  Covers: the landing page at "/"; the "/" redirect for signed-in visitors;
  the move of the timeline to "/feed" (including the paging fragment and every
  query parameter); the wordmark, Timeline tab, group, Favourites, Later and
  search links and htmx targets; plain-form redirects (mark read, favourite,
  read later, open fallback); the sign-out control and its destination;
  default login, setup and signed-in "/login" destinations.

  Supersedes (the coder must update these existing files, mechanically
  "/" -> "/feed" and "/?x" -> "/feed?x" for the timeline, plus the matching
  hard-coded paths in the step definitions and Browser helpers):
    - chronological-feed.feature: description ("home page /", "/?show=all")
      and every `I request the home page "/"` / `"/?show=all"` scenario.
    - favourites.feature: "/", "/?show=all", "/?favourites=1[&show=all]", the
      Favourites navigation entry linking to "/?favourites=1" (now
      "/feed?favourites=1"), and the plain-post redirect to "/" (now "/feed").
    - read-later.feature: "/", "/?show=all", "/?later=1[&show=all]",
      "/?favourites=1&later=1", the Later entry linking to "/?later=1", and
      the plain-post redirect to "/" (now "/feed").
    - full-text-search.feature: "/?q=match", "/?q=plain&favourites=1",
      "/?favourites=1", "/" and its description ("GET /?q=<text>").
    - source-groups.feature: every `home page "/"`, the description
      ("/?group=<id>") and the unknown-group outline rows "/?group=99999" and
      "/?group=abc" (now "/feed?group=...").
    - sources-keeps-playing.feature: `I request the page "/" via htmx` and
      the outline row "/" in "Both pages carry the dock..." (now "/feed");
      the Timeline tab step assertions expect href and hx-get "/feed".
    - theme-switch.feature: outline row "/" (now "/feed").
    - reader-pane.feature, docked-player.feature, embedded-playback.feature,
      player-deck.feature, media-detail-view.feature, post-images.feature,
      feed-ingestion.feature, full-article-fetch.feature: every
      `I request the home page "/"` -> "/feed" (not the feed-server
      `serves "/"` steps in post-images.feature, which are unrelated).
    - project-skeleton.feature: "Home page shows the app name and an empty
      state" requests "/feed".
    - accounts-login.feature, description and scenarios:
        * description: "/" is public (the landing page) once a user exists;
          logout shows the landing page; signed-in "/login" redirects to
          "/feed"; drop "the public landing page, /feed and the top-bar
          logout control (#42)" from its out-of-scope list.
        * "With no users, every page redirects to setup": unchanged ("/").
        * "Completing setup creates an admin and signs them in": redirect
          "/feed", and `requesting "/feed" now returns status 200`.
        * "Protected pages send an anonymous visitor to login": row "/"
          becomes "/feed".
        * "Logging in without a pending page lands on the home page":
          redirects to "/feed" (rename to "... lands on the timeline").
        * every `requesting "/" now returns status 200`, `still redirects to
          "/login"`, `redirects to "/login"` and `can request "/" with status
          200` becomes "/feed" (anonymous "/" now returns the landing page
          with status 200): "Usernames are case-insensitive", "Wrong
          credentials get one generic error", "Five failures in a row block
          that client...", "The block lifts after 15 minutes", "A success
          resets the failure count", "Another client is not blocked...",
          "Without remember me...", both remember-me scenarios and "Logout
          ends the session and forgets remember me".
        * "Logout ends the session and forgets remember me": the logout
          redirect is "/" instead of "/login".
        * "A signed-in visitor to the login page is sent to the timeline" and
          "A visitor restored by remember me is sent from the login page to
          the timeline": redirect "/feed".
        * Non-goal "Top-bar logout control and moving the timeline to /feed
          (#42)" is now done.
    - Any other occurrence found with
      `grep -rn 'home page "/\|page "/"' specs/features`.

  Out of scope: the "Sign up" link (#44); per-user data (#43); account page
  and password change (#45). Browser behaviour (the theme actually flipping,
  the 375px layout, the shutter, htmx history) is covered by the human
  playtest, as in theme-switch.feature; exact landing wording is settled at
  playtest, so scenarios pin structure and key terms only.

  Background:
    Given a fresh SQLite database file

  # ---------- The landing page ----------

  Scenario: An anonymous visitor sees the landing page
    Given the application is running
    And I am not signed in
    When I request "/"
    Then the response status is 200
    And the response is a complete HTML document
    And the page contains "Starve"
    And the page contains "YouTube"
    And the page contains "SoundCloud"
    And the page contains "OPML"
    And the landing page has a "Sign in" link to "/login"

  Scenario: The landing page has no screenshots, no sign-up link and no sign-out control
    Given the application is running
    And I am not signed in
    When I request "/"
    Then the page contains no images
    And the page does not contain "Sign up"
    And the page has no sign-out control

  Scenario: The landing page follows the theme switch and phone width
    Given the application is running
    And I am not signed in
    When I request "/"
    Then the page has a theme switch
    And the page loads the theme script before its stylesheet
    And the page contains a viewport meta tag

  Scenario: The landing page ignores any query string for anonymous visitors
    Given the application is running
    And I am not signed in
    When I request "/?favourites=1"
    Then the response status is 200
    And the landing page has a "Sign in" link to "/login"

  Scenario: Anonymous pages keep the wordmark at the landing page
    Given the application is running
    And I am not signed in
    Then the wordmark on "/" links to "/"
    And the wordmark on "/login" links to "/"

  Scenario: While no user exists the home page still redirects to setup
    Given the application is running with no users yet
    When I request "/"
    Then the response redirects to "/setup"

  # ---------- Signed-in visitors at "/" ----------

  Scenario: A signed-in visitor to the home page is redirected to the timeline
    Given the application is running
    When I request "/"
    Then the response redirects to "/feed"

  Scenario Outline: An old timeline URL keeps its query when redirected
    Given the application is running
    When I request "<old>"
    Then the response redirects to "<new>"

    Examples:
      | old            | new                |
      | /?show=all     | /feed?show=all     |
      | /?favourites=1 | /feed?favourites=1 |
      | /?later=1      | /feed?later=1      |
      | /?q=a%20b      | /feed?q=a%20b      |
      | /?group=7      | /feed?group=7      |

  Scenario: A visitor restored by remember me is redirected from the home page to the timeline
    Given the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And I log in as "rob" with password "correct-horse-1" ticking "Remember me"
    When the browser session ends
    And I request "/"
    Then the response redirects to "/feed"

  # ---------- The timeline at /feed ----------

  Scenario: The timeline is served at /feed
    Given the application is running
    And the source "Daily" has an unread item "First post" published 1 days ago
    When I request the home page "/feed"
    Then the response status is 200
    And the timeline lists "First post"

  Scenario: The empty timeline at /feed points at Sources
    Given the application is running
    When I request the home page "/feed"
    Then the response status is 200
    And the page contains "No sources yet"
    And the response contains a link to "/sources"

  Scenario: The /feed views keep their parameters
    Given the application is running
    And the source "Daily" has an unread item "First post" published 1 days ago
    When I request the home page "/feed?show=all"
    Then the response status is 200
    And the timeline lists "First post"

  Scenario: Timeline links point at /feed
    Given the application is running
    And the group "Music" exists
    And the source "Daily" has an unread item "First post" published 1 days ago
    When I request the home page "/feed"
    Then the wordmark links to "/feed"
    And the Timeline tab links to "/feed" and requests "/feed" via htmx
    And the navigation has the "Favourites" entry linking to "/feed?favourites=1"
    And the navigation ends with the "Later" entry linking to "/feed?later=1"
    And the search form submits to "/feed"

  Scenario: The Sources and Import pages link back to /feed
    Given the application is running
    When I request "/sources"
    Then the wordmark links to "/feed"
    And the Timeline tab links to "/feed" and requests "/feed" via htmx
    When I request "/import"
    Then the wordmark links to "/feed"
    And the Timeline tab links to "/feed"

  Scenario: Paging requests the next page of /feed
    Given the application is running
    And the source "Daily" has 60 unread items
    When I request the home page "/feed"
    Then the "Load more" link requests a "/feed?after=" URL
    When I follow the "Load more" link via htmx
    Then the response is only the next page of items, not a full document

  Scenario Outline: Plain form posts redirect back to the timeline at /feed
    Given the application is running
    And the source "Daily" has an unread item "First post" published 1 days ago
    When I submit "<action>" for "First post" without htmx
    Then the response redirects to "/feed"

    Examples:
      | action     |
      | mark read  |
      | favourite  |
      | read later |

  Scenario: An anonymous visitor to /feed is sent to login
    Given the application is running
    And I am not signed in
    Then requesting "/feed" redirects to "/login"
    And requesting "/feed?after=abc" also redirects to "/login"

  Scenario: An htmx request for /feed without a session reloads the whole page on login
    Given the application is running
    And I am not signed in
    When I request the page "/feed" via htmx
    Then the response tells htmx to load "/login" as a full page

  Scenario: Logging in resumes a requested /feed page
    Given the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And I have requested "/feed?favourites=1"
    When I log in as "rob" with password "correct-horse-1"
    Then the response redirects to "/feed?favourites=1"

  # ---------- Default destinations ----------

  Scenario: Logging in without a pending page lands on the timeline
    Given the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    When I log in as "rob" with password "correct-horse-1"
    Then the response redirects to "/feed"

  Scenario: Completing setup lands on the timeline
    Given the application is running with no users yet
    When I complete setup with the logged code as "rob" with password "correct-horse-1"
    Then the response redirects to "/feed"

  Scenario: A signed-in visitor to the login page is sent to the timeline
    Given the application is running
    Then requesting "/login" redirects to "/feed"

  # ---------- Signing out ----------

  Scenario Outline: Every signed-in page has a sign-out control
    Given the application is running
    When I request "<page>"
    Then the response status is 200
    And the top bar has a "Sign out" button that posts "/logout" with the CSRF token
    And the "Sign out" form works without JavaScript

    Examples:
      | page     |
      | /feed    |
      | /sources |
      | /import  |

  Scenario: The sign-out control survives the out-of-band bar swap
    Given the application is running
    When I request the page "/sources" via htmx
    Then the response contains the top bar with the "Sign out" button
    When I request the page "/feed" via htmx
    Then the response contains the top bar with the "Sign out" button

  Scenario: The login page has no sign-out control
    Given the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    Then the page "/login" has no sign-out control

  Scenario: Signing out returns to the landing page
    Given the application is running
    When I sign out with the top bar control
    Then the response redirects to "/"
    And requesting "/" returns the landing page with a "Sign in" link to "/login"
    And requesting "/feed" redirects to "/login"

  Scenario: Signing out also forgets remember me
    Given the application is running with the user "rob" with password "correct-horse-1"
    And I am not signed in
    And I log in as "rob" with password "correct-horse-1" ticking "Remember me"
    When I sign out with the top bar control
    Then the persistent login cookie is deleted
    And requesting "/feed" redirects to "/login"

  Scenario: Signing out needs the CSRF token
    Given the application is running
    When I post "/logout" without its CSRF token
    Then the response status is 403
    And requesting "/feed" now returns status 200

  # Non-goals:
  #   - A "Sign up" link or any registration (#44); per-user data (#43);
  #     account page (#45).
  #   - Screenshots, animation or analytics on the landing page; exact copy.
  #   - Real browser behaviour: theme flipping, 375px layout, the shutter,
  #     htmx history/Back after the path change: human playtest.
  #
  # Risks:
  #   - The move touches every hard-coded "/" for the timeline: templates
  #     (home, sources, import: wordmark, Timeline tab href and hx-get, search
  #     form action and hx-get), HomeController's mapping and its link()
  #     helper, redirects in HomeController, FavouriteController,
  #     LaterController, the open fallback, LoginController, SetupController,
  #     SecurityConfig (defaultSuccessUrl, logoutSuccessUrl), deck.js/theme.js
  #     if they match on "/" for tab handling, and the acceptance step
  #     definitions. Miss one and a link lands on the landing page.
  #   - "/" must become public (SecurityConfig PUBLIC_PATHS) without
  #     weakening "no users yet -> /setup"; "/" with a query must show only
  #     the landing page to anonymous visitors, never the timeline.
  #   - The redirect from "/" must preserve the query string (URL-encoded
  #     input such as q=a%20b must round-trip unchanged).
  #   - The sign-out form needs the CSRF token inside #bar, including in the
  #     fragments the htmx tab swaps return; a bar without the token would
  #     make Sign out 403 after a tab click.
  #   - Step definitions that hard-code "/" for the Timeline tab or timeline
  #     assertions (sources-keeps-playing, theme-switch) need the new path.
  #
  # Open questions: none (all resolved, see specs/intent/public-home-page.md
  # Clarifications; REAL auto-decision: a signed-in "/?query" redirects to
  # "/feed?query", keeping the query).
