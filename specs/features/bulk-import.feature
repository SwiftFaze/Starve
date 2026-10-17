Feature: Bulk import
  One upload form on "/import" (linked from "/sources") imports many
  subscriptions at once from either a YouTube Takeout "subscriptions.csv" or an
  OPML file. The file type is detected by content, not extension: an XML
  document whose root is <opml> is OPML; text whose header row contains
  "Channel Id" is a YouTube Takeout CSV; anything else is rejected with
  "unrecognised file: expected a YouTube subscriptions CSV or an OPML file".
  Files over 1 MB are rejected with "file is too large (max 1 MB)".

  CSV: each row's channel id (UC followed by 22 of [A-Za-z0-9_-]) becomes the
  subscription https://www.youtube.com/feeds/videos.xml?channel_id=<id>, titled
  with the row's Channel Title. A BOM, CRLF line endings and quoted titles
  containing commas are accepted. Invalid rows are reported as failed with the
  row number and reason.

  OPML: every <outline> with an http(s) xmlUrl becomes a subscription to that
  URL as-is, titled with its title (fallback text). An outline without xmlUrl
  that contains outlines is a folder: its feeds are assigned to a group named
  after the folder's title (fallback text), created if missing or reused
  case-insensitively; nested folders use the nearest folder; top-level feeds
  stay ungrouped; names over 50 characters are truncated to 50. A DOCTYPE is
  rejected (no DTD or external entity is ever resolved).

  Imports store subscriptions immediately without fetching in the request (the
  feed's own title replaces the provisional one on first fetch), then trigger
  one non-blocking background poll of just the newly added sources. A summary
  shows added / already subscribed / failed counts and the failures with
  reasons. A feed URL already subscribed counts as "already subscribed" (an
  OPML import still assigns it to the folder's group if it is ungrouped).

  Covers: "/import" and its upload form; CSV and OPML parsing; group mapping of
  OPML folders; deduplication; the summary; the post-import background poll;
  the bar on "/import" marking Sources as the current page (aria-current, #106),
  since Import is reached from Sources.
  Supersedes: nothing. Deliberately does not run the smart-add page resolver
  per row (rows already carry channel ids or feed URLs), so no page is fetched
  during import.
  Out of scope: OPML export; SoundCloud/X/Instagram follow lists.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running
    And YouTube feeds are configured to be served by the feed server

  Scenario: The sources page links to the import page
    When I request "/sources"
    Then the page contains "/import"

  Scenario: The import page's bar marks Sources as the current page
    When I request "/import"
    Then the bar marks only "Sources" as the current page

  Scenario: A Takeout CSV adds a subscription per channel with the CSV title
    When I import the file "subscriptions.csv" containing:
      """
      Channel Id,Channel Url,Channel Title
      UCaaaaaaaaaaaaaaaaaaaa01,http://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaa01,Alpha Channel
      UCaaaaaaaaaaaaaaaaaaaa02,http://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaa02,Beta Channel
      """
    Then the import summary shows 2 added, 0 already subscribed and 0 failed
    And the sources page lists a source titled "Alpha Channel"
    And the sources page lists a source titled "Beta Channel"
    And the source "Alpha Channel" is subscribed at the YouTube feed for channel "UCaaaaaaaaaaaaaaaaaaaa01"
    And there are 2 subscriptions

  Scenario: A CSV with a BOM, CRLF line endings and quoted titles containing commas imports
    When I import the file "subscriptions.csv" with a byte order mark and CRLF line endings containing:
      """
      Channel Id,Channel Url,Channel Title
      UCaaaaaaaaaaaaaaaaaaaa01,http://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaa01,"Doe, Jane & Friends"
      UCaaaaaaaaaaaaaaaaaaaa02,http://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaa02,Plain Title
      """
    Then the import summary shows 2 added, 0 already subscribed and 0 failed
    And the sources page lists a source titled "Doe, Jane & Friends"
    And the sources page lists a source titled "Plain Title"

  Scenario: CSV rows with an invalid channel id are reported by row number while valid rows import
    When I import the file "subscriptions.csv" containing:
      """
      Channel Id,Channel Url,Channel Title
      UCaaaaaaaaaaaaaaaaaaaa01,http://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaa01,Alpha Channel
      not-an-id,http://www.youtube.com/channel/not-an-id,Broken One
      UCaaaaaaaaaaaaaaaaaaaa02,http://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaa02,Beta Channel
      """
    Then the import summary shows 2 added, 0 already subscribed and 1 failed
    And the import summary lists the failure "row 3: invalid channel id"
    And the sources page does not list a source titled "Broken One"
    And there are 2 subscriptions

  Scenario: An OPML file adds a subscription per feed outline
    Given the feed server serves "/a.xml" as an RSS 2.0 feed titled "Feed A" with 1 entries
    And the feed server serves "/b.xml" as an RSS 2.0 feed titled "Feed B" with 1 entries
    When I import the file "feeds.opml" containing the feed server OPML:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline type="rss" title="Provisional A" xmlUrl="{feedserver}/a.xml"/>
        <outline type="rss" text="Provisional B" xmlUrl="{feedserver}/b.xml"/>
      </body></opml>
      """
    Then the import summary shows 2 added, 0 already subscribed and 0 failed
    And the sources page lists a source titled "Provisional A"
    And the sources page lists a source titled "Provisional B"
    And the source "Provisional A" is subscribed at the feed URL "/a.xml"

  Scenario: OPML outlines that are not http(s) feed URLs are reported as failed
    Given the feed server serves "/a.xml" as an RSS 2.0 feed titled "Feed A" with 1 entries
    When I import the file "feeds.opml" containing the feed server OPML:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline type="rss" title="Good" xmlUrl="{feedserver}/a.xml"/>
        <outline type="rss" title="Foo" xmlUrl="ftp://example.com/feed.xml"/>
      </body></opml>
      """
    Then the import summary shows 1 added, 0 already subscribed and 1 failed
    And the import summary lists the failure "outline 'Foo': not an http(s) URL"

  Scenario: OPML folders become groups and a folder name matches an existing group case-insensitively
    Given the group "news" exists
    And the feed server serves "/n1.xml" as an RSS 2.0 feed titled "News One" with 1 entries
    And the feed server serves "/m1.xml" as an RSS 2.0 feed titled "Music One" with 1 entries
    When I import the file "feeds.opml" containing the feed server OPML:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline title="News">
          <outline type="rss" title="News One" xmlUrl="{feedserver}/n1.xml"/>
        </outline>
        <outline text="Music">
          <outline type="rss" title="Music One" xmlUrl="{feedserver}/m1.xml"/>
        </outline>
      </body></opml>
      """
    Then the import summary shows 2 added, 0 already subscribed and 0 failed
    And the source "News One" is in the group "news"
    And the source "Music One" is in the group "Music"
    And there are 2 groups

  Scenario: Nested OPML folders use the nearest folder
    Given the feed server serves "/deep.xml" as an RSS 2.0 feed titled "Deep" with 1 entries
    When I import the file "feeds.opml" containing the feed server OPML:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline title="Outer">
          <outline title="Inner">
            <outline type="rss" title="Deep" xmlUrl="{feedserver}/deep.xml"/>
          </outline>
        </outline>
      </body></opml>
      """
    Then the source "Deep" is in the group "Inner"

  Scenario: Top-level OPML feeds stay ungrouped
    Given the feed server serves "/top.xml" as an RSS 2.0 feed titled "Top" with 1 entries
    When I import the file "feeds.opml" containing the feed server OPML:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline type="rss" title="Top" xmlUrl="{feedserver}/top.xml"/>
      </body></opml>
      """
    Then the source "Top" is ungrouped
    And there are 0 groups

  Scenario: A folder name over 50 characters is truncated to 50
    Given the feed server serves "/long.xml" as an RSS 2.0 feed titled "Long" with 1 entries
    When I import the file "feeds.opml" containing the feed server OPML:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline title="012345678901234567890123456789012345678901234567890123456789">
          <outline type="rss" title="Long" xmlUrl="{feedserver}/long.xml"/>
        </outline>
      </body></opml>
      """
    Then the source "Long" is in the group "01234567890123456789012345678901234567890123456789"

  Scenario: Re-importing the same CSV adds nothing
    Given I have imported the file "subscriptions.csv" containing:
      """
      Channel Id,Channel Url,Channel Title
      UCaaaaaaaaaaaaaaaaaaaa01,http://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaa01,Alpha Channel
      UCaaaaaaaaaaaaaaaaaaaa02,http://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaa02,Beta Channel
      """
    When I import the file "subscriptions.csv" containing:
      """
      Channel Id,Channel Url,Channel Title
      UCaaaaaaaaaaaaaaaaaaaa01,http://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaa01,Alpha Channel
      UCaaaaaaaaaaaaaaaaaaaa02,http://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaa02,Beta Channel
      """
    Then the import summary shows 0 added, 2 already subscribed and 0 failed
    And there are 2 subscriptions

  Scenario: Re-importing the same OPML adds nothing and creates no duplicate group
    Given the feed server serves "/n1.xml" as an RSS 2.0 feed titled "News One" with 1 entries
    And I have imported the file "feeds.opml" containing the feed server OPML:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline title="News">
          <outline type="rss" title="News One" xmlUrl="{feedserver}/n1.xml"/>
        </outline>
      </body></opml>
      """
    When I import the file "feeds.opml" containing the feed server OPML:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline title="News">
          <outline type="rss" title="News One" xmlUrl="{feedserver}/n1.xml"/>
        </outline>
      </body></opml>
      """
    Then the import summary shows 0 added, 1 already subscribed and 0 failed
    And there is 1 subscription
    And there is 1 group

  Scenario: An already-subscribed ungrouped feed gets the folder's group
    Given the feed server serves "/n1.xml" as an RSS 2.0 feed titled "News One" with 1 entries
    And I have added the feed URL "/n1.xml"
    When I import the file "feeds.opml" containing the feed server OPML:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline title="News">
          <outline type="rss" title="News One" xmlUrl="{feedserver}/n1.xml"/>
        </outline>
      </body></opml>
      """
    Then the import summary shows 0 added, 1 already subscribed and 0 failed
    And the source "News One" is in the group "News"

  Scenario: An already-subscribed feed that is already grouped keeps its group
    Given the feed server serves "/n1.xml" as an RSS 2.0 feed titled "News One" with 1 entries
    And I have added the feed URL "/n1.xml"
    And the group "Favourites" exists
    And the source "News One" is in the group "Favourites"
    When I import the file "feeds.opml" containing the feed server OPML:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline title="News">
          <outline type="rss" title="News One" xmlUrl="{feedserver}/n1.xml"/>
        </outline>
      </body></opml>
      """
    Then the source "News One" is in the group "Favourites"

  Scenario: A file that is neither a Takeout CSV nor OPML is rejected
    When I import the file "notes.txt" containing:
      """
      just some text
      with no header
      """
    Then the import page shows the message "unrecognised file: expected a YouTube subscriptions CSV or an OPML file"
    And there are no subscriptions

  Scenario: An XML document whose root is not opml is rejected
    When I import the file "feed.xml" containing:
      """
      <?xml version="1.0"?>
      <rss version="2.0"><channel><title>x</title></channel></rss>
      """
    Then the import page shows the message "unrecognised file: expected a YouTube subscriptions CSV or an OPML file"
    And there are no subscriptions

  Scenario: The file type is detected by content, not extension
    When I import the file "export.dat" containing:
      """
      Channel Id,Channel Url,Channel Title
      UCaaaaaaaaaaaaaaaaaaaa01,http://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaa01,Alpha Channel
      """
    Then the import summary shows 1 added, 0 already subscribed and 0 failed

  Scenario: A file over 1 MB is rejected
    When I import a file "subscriptions.csv" of 2 MB
    Then the import page shows the message "file is too large (max 1 MB)"
    And there are no subscriptions

  Scenario: An OPML file with a DOCTYPE is rejected without resolving it
    Given the feed server records requests
    When I import the file "evil.opml" containing the feed server OPML:
      """
      <?xml version="1.0"?>
      <!DOCTYPE opml [<!ENTITY xxe SYSTEM "{feedserver}/xxe-probe">]>
      <opml version="2.0"><body>
        <outline type="rss" title="&xxe;" xmlUrl="{feedserver}/a.xml"/>
      </body></opml>
      """
    Then the import page shows the message "unrecognised file: expected a YouTube subscriptions CSV or an OPML file"
    And the feed server received no request for "/xxe-probe"
    And there are no subscriptions

  Scenario: Importing does not fetch anything in the request
    Given the feed server records requests
    When I import the file "feeds.opml" containing the feed server OPML without waiting for the background poll:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline type="rss" title="Provisional A" xmlUrl="{feedserver}/a.xml"/>
      </body></opml>
      """
    Then the import response arrived before any request for "/a.xml"
    And the sources page lists a source titled "Provisional A"

  Scenario: Imported sources get their items and real title after the triggered background poll
    Given the feed server serves "/a.xml" as an RSS 2.0 feed titled "Real Title A" with 3 entries
    When I import the file "feeds.opml" containing the feed server OPML:
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0"><body>
        <outline type="rss" title="Provisional A" xmlUrl="{feedserver}/a.xml"/>
      </body></opml>
      """
    And the import's background poll completes
    Then the sources page lists a source titled "Real Title A"
    And the source "Real Title A" has 3 stored items

  Scenario: Imported YouTube channels get their items after the triggered background poll
    Given the feed server serves "/youtube/feeds/videos.xml" as an Atom feed titled "Alpha Channel Feed" with 2 entries
    When I import the file "subscriptions.csv" containing:
      """
      Channel Id,Channel Url,Channel Title
      UCaaaaaaaaaaaaaaaaaaaa01,http://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaa01,Alpha Channel
      """
    And the import's background poll completes
    Then the source "Alpha Channel Feed" has 2 stored items

# Non-goals:
#   - OPML export; SoundCloud/X/Instagram follow lists.
#   - Running the smart-add page resolver per row, or scraping channel ids
#     from HTML; CSV rows already carry the id, OPML rows already carry feed URLs.
#   - Progress UI for the background poll; items just appear on the timeline.
#
# Risks:
#   - XXE / entity expansion: the OPML parser must disable DTDs and external
#     entities; covered by the DOCTYPE scenario.
#   - Takeout format drift (column names/order): detection keys on the
#     "Channel Id" header and reads columns by header name.
#   - New steps and fixtures: multipart upload helper, "{feedserver}"
#     placeholder substitution in OPML text, request recording on the feed
#     server, a deterministic await of the post-import poll, and a test
#     property for the YouTube feed base URL (default
#     https://www.youtube.com/feeds/videos.xml) pointing at the fixture server.
#   - A large import creates many rows in one request; accepted for a 1 MB cap
#     (single user).
#
# Open questions:
#   - None. Decisions are recorded under Clarifications in
#     specs/intent/bulk-import.md.
