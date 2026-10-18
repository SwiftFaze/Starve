Feature: Smart-add for platforms without a feed link
  Reddit, Vimeo, GitHub users and Apple Podcasts publish feeds their pages do
  not advertise, so pasting one of their pages used to be rejected with "no
  feed found" (#145). Smart-add now recognises their pages by URL, before
  fetching anything, and subscribes to the feed at the platform's known
  address.

  Covers: a subreddit page (/r/<name>) subscribing to /r/<name>/.rss; a Vimeo
  channel (/channels/<name>) or user (/<name>, not a numeric video id) to its
  /videos/rss; a GitHub user (/<user>) to /<user>.atom; an Apple Podcasts show
  link (ending in id<digits>) to the feedUrl the iTunes lookup API names for
  that id; the usual duplicate checks and validation of the resolved feed (one
  that does not answer is "could not be fetched", a lookup without a feedUrl is
  "no feed found at this address"); pages that already advertise their feed
  (Bluesky and Mastodon profiles, GitHub release pages, Substack) still
  resolving through it; bulk import storing a Reddit, Vimeo or GitHub page entry
  as its feed, and reporting an Apple Podcasts entry as failed, since its lookup
  would be a fetch during the upload. Hosts and feed addresses are configurable
  per platform (starve.reddit.*, starve.vimeo.*, starve.github.*,
  starve.apple-podcasts.*); these scenarios point them at the feed server.
  Supersedes: nothing; the rules run before smart-add.feature's resolution
  order, only for URLs on a configured host.
  Out of scope: inline Vimeo playback (#149); podcast audio playback (#148);
  Bandcamp; Reddit user pages, multireddits and search feeds.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: A subreddit page subscribes to the subreddit's feed without fetching the page
    Given the "Reddit" rule uses the feed server
    And the feed server records requests
    And the feed server serves "/reddit/r/selfhosted/.rss" as an Atom feed titled "r/selfhosted" with 2 entries
    When I add the feed URL "/r/selfhosted/"
    Then the sources page lists a source titled "r/selfhosted"
    And the source "r/selfhosted" is subscribed at the feed URL "/reddit/r/selfhosted/.rss"
    And the feed server received no request for "/r/selfhosted/"

  Scenario: A Vimeo channel subscribes to the channel's videos feed
    Given the "Vimeo" rule uses the feed server
    And the feed server serves "/vimeo/channels/staffpicks/videos/rss" as an RSS 2.0 feed titled "Staff Picks" with 2 entries
    When I add the feed URL "/channels/staffpicks"
    Then the source "Staff Picks" is subscribed at the feed URL "/vimeo/channels/staffpicks/videos/rss"

  Scenario: A Vimeo user subscribes to the user's videos feed
    Given the "Vimeo" rule uses the feed server
    And the feed server serves "/vimeo/someone/videos/rss" as an RSS 2.0 feed titled "Someone" with 1 entries
    When I add the feed URL "/someone"
    Then the source "Someone" is subscribed at the feed URL "/vimeo/someone/videos/rss"

  Scenario: A GitHub user subscribes to the user's activity feed
    Given the "GitHub" rule uses the feed server
    And the feed server serves "/github/torvalds.atom" as an Atom feed titled "torvalds" with 3 entries
    When I add the feed URL "/torvalds"
    Then the source "torvalds" is subscribed at the feed URL "/github/torvalds.atom"
    And the source "torvalds" has 3 stored items

  Scenario: An Apple Podcasts show subscribes to the feed its lookup names
    Given the "Apple Podcasts" rule uses the feed server
    And the feed server serves "/shows/hard-fork.xml" as an RSS 2.0 feed titled "Hard Fork" with 2 entries
    And the feed server answers the lookup of podcast "1528594034" with the feed "/shows/hard-fork.xml"
    When I add the feed URL "/us/podcast/hard-fork/id1528594034"
    Then the source "Hard Fork" is subscribed at the feed URL "/shows/hard-fork.xml"

  Scenario: An Apple Podcasts show whose lookup names no feed is rejected
    Given the "Apple Podcasts" rule uses the feed server
    And the feed server answers the lookup of podcast "42" with no feed
    When I add the feed URL "/us/podcast/gone/id42"
    Then the sources page shows the message "no feed found at this address"
    And there are no subscriptions

  Scenario: A recognised page whose feed does not answer is rejected
    Given the "Reddit" rule uses the feed server
    And the feed server responds to "/reddit/r/banned/.rss" with HTTP 404
    When I add the feed URL "/r/banned"
    Then the sources page shows the message "could not be fetched"
    And there are no subscriptions

  Scenario: A recognised page whose feed is already followed is not added twice
    Given the "GitHub" rule uses the feed server
    And the feed server serves "/github/torvalds.atom" as an Atom feed titled "torvalds" with 3 entries
    And I have added the feed URL "/github/torvalds.atom"
    When I add the feed URL "/torvalds"
    Then the sources page shows the message "already subscribed"
    And there is 1 subscription

  Scenario Outline: Pages that advertise their feed still resolve through it
    Given every platform rule uses the feed server
    And the feed server serves "/feeds/<name>.xml" as an RSS 2.0 feed titled "<title>" with 1 entries
    And the feed server serves "<page>" as an HTML page whose head links the alternate feed "/feeds/<name>.xml" of type "application/rss+xml"
    When I add the feed URL "<page>"
    Then the source "<title>" is subscribed at the feed URL "/feeds/<name>.xml"

    Examples:
      | page                            | name     | title             |
      | /profile/someone.bsky.social    | bluesky  | Bluesky someone   |
      | /@someone                       | mastodon | Mastodon someone  |
      | /torvalds/linux/releases        | releases | Linux releases    |
      | /p/a-substack-post              | substack | A Substack        |

  Scenario: Bulk import stores a platform page as its feed and reports an Apple Podcasts link
    Given the "Reddit" rule uses the feed server
    And the feed server records requests
    And the feed server serves "/reddit/r/selfhosted/.rss" as an Atom feed titled "r/selfhosted" with 2 entries
    When I import the file "feeds.opml" containing the feed server OPML:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline type="rss" title="Self-hosted" xmlUrl="{feedserver}/r/selfhosted/"/>
        <outline type="rss" title="Hard Fork" xmlUrl="https://podcasts.apple.com/us/podcast/hard-fork/id1528594034"/>
      </body></opml>
      """
    Then the import summary shows 1 added, 0 already subscribed and 1 failed
    And the import summary lists the failure "'Hard Fork': an Apple Podcasts link, add it on Sources"
    When the import's background poll completes
    Then the source "r/selfhosted" is subscribed at the feed URL "/reddit/r/selfhosted/.rss"
    And the feed server received no request for "/r/selfhosted/"
