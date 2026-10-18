Feature: Items from feed-only hosts show their feed entry, never their fetched page
  GitHub and Bluesky feed entries already are the whole post, but they are
  shorter than the teaser threshold, and both sites render with JavaScript,
  so fetching their page stored the page chrome as the body and the account
  avatar as the thumbnail. Items whose link is on a feed-only host are never
  treated as teasers: the reader shows the feed entry and never fetches the
  page.

  Covers: the feed-only host list (configuration starve.reader.feed-only-hosts,
  default github.com, www.github.com and bsky.app, matched exactly ignoring
  case); opening such an item showing its stored body, recording no article
  check and taking no og:image; migration V19 clearing the body, thumbnail and
  article check (and the search row's body text) of items on the default
  hosts that were article-checked, so the next poll fills body and thumbnail
  back in from the feed (pinned by FeedOnlyHostsMigrationTest).
  Supersedes: in part full-article-fetch.feature, whose teaser fetch now
  applies only to links outside the feed-only hosts.
  Out of scope: extracting JavaScript-rendered pages; untitled Bluesky and
  Mastodon items; image proxying.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario Outline: A short item from a feed-only host shows its feed entry and its page is never fetched
    Given the source "Blog" has an item "Post" with the link "<link>" and no stored body and no thumbnail published 1 days ago
    And the source "Blog" has an item "Short" with the link "<link>/short" and the stored body "<p>Fixed the build.</p>" published 1 days ago
    When I request the reader of the item "Short" via htmx
    Then the response status is 200
    And the reader body contains "Fixed the build."
    And the item "Short" has no recorded article check
    When I request the reader of the item "Post" via htmx
    Then the item "Post" has no recorded article check
    And the item "Post" has no stored thumbnail

    Examples:
      | link                                          |
      | https://github.com/torvalds/linux/commit/abc  |
      | https://WWW.GitHub.com/SwiftFaze/Starve/releases/tag/v1 |
      | https://bsky.app/profile/bsky.app/post/3abc   |

  Scenario: The feed-only host list is configurable
    Given the reader treats "localhost" as its only feed-only host
    And the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and the stored body "<p>Just a teaser.</p>" published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text
    When I request the reader of the item "Post" via htmx
    Then the feed server received 0 requests for "/posts/one"
    And the reader body contains "Just a teaser."

  Scenario: A teaser from any other host still has its article fetched
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and the stored body "<p>Just a teaser.</p>" published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text
    When I request the reader of the item "Post" via htmx
    Then the reader body contains the article text

# Non-goals:
#   - Site-specific extraction for GitHub or Bluesky pages.
#
# Risks:
#   - Items repaired by V19 that have dropped out of their feed keep no body;
#     the reader shows "Open original" for them.
#
# Open questions:
#   - None (config key, exact host matching and the V19 repair are recorded as
#     auto-decided in the intent doc).
