Feature: Podcast episodes play in the deck
  Podcast feeds subscribed fine, but their episodes only linked out. An
  episode's audio enclosure is now stored with it, and the episode plays in
  the deck through an <audio> element, driven by the deck's own controls,
  exactly where a SoundCloud track plays.

  Covers: capturing an entry's audio file at ingestion (an enclosure whose
  type is audio/*, else a media:content whose type is audio/* or whose medium
  is audio), resolved to an absolute http or https URL, with its length from
  itunes:duration (seconds, MM:SS or HH:MM:SS), else the media:content's
  duration; an audio_url and audio_duration on items (migration V22); an
  episode being playable (Play control, title opening the player, 404 for
  the reader) unless its link is itself a YouTube video or SoundCloud track,
  which keeps playing as before; the player fragment holding an <audio>
  element (preload metadata) with the deck's transport (a plain progress
  line instead of a waveform), the total time from the feed until the audio
  reports its own, and a hidden notice "This episode can't be played here"
  with a link to the episode's page (else the audio file) that deck.js shows
  when the audio fails to load; playing marking the episode read; an episode
  without art of its own showing the show's art (the channel's itunes:image,
  else its RSS image) as its thumbnail and deck art.
  Supersedes: in part embedded-playback.feature, whose playable kinds were
  YouTube and SoundCloud links only, and post-images.feature's channel-image
  rule, which still holds for everything but podcast episodes.
  Out of scope: remembering the playback position (#150); video enclosures;
  playback speed; downloads; chapters; proxying the audio; episodes stored
  before this (ingestion only inserts new items, so they keep linking out);
  how the deck sounds and looks while playing: the human playtest.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: An episode with an audio enclosure plays in the deck
    Given the feed server serves "/pod.xml" as a podcast titled "Pod" whose episode has the audio enclosure "https://cdn.example/ep1.mp3" lasting "1:02:03"
    When I add the feed URL "/pod.xml"
    And I request the home page "/feed"
    Then the timeline entry "Pod 1" has a Play control
    When I request the player of the item "Pod 1" via htmx
    Then the response status is 200
    And the player plays the audio "https://cdn.example/ep1.mp3"
    And the player shows the total time "1:02:03"
    And the response contains no iframe
    And the item "Pod 1" is read

  Scenario Outline: The episode's length is read from the feed
    Given the feed server serves "/pod.xml" as a podcast titled "Pod" whose episode has the audio enclosure "https://cdn.example/ep1.mp3" lasting "<duration>"
    When I add the feed URL "/pod.xml"
    And I request the player of the item "Pod 1" via htmx
    Then the player shows the total time "<shown>"

    Examples:
      | duration | shown   |
      | 2472     | 41:12   |
      | 41:12    | 41:12   |
      | 01:00:00 | 1:00:00 |
      | soon     | --:--   |

  Scenario: An audio media:content is an episode too
    Given the feed server serves "/pod.xml" as a podcast titled "Pod" whose episode has the audio media:content "https://cdn.example/ep1.m4a" of type "audio/mp4"
    When I add the feed URL "/pod.xml"
    And I request the player of the item "Pod 1" via htmx
    Then the player plays the audio "https://cdn.example/ep1.m4a"

  Scenario: A relative enclosure resolves against the episode's page
    Given the feed server serves "/pod.xml" as a podcast titled "Pod" whose episode has the audio enclosure "/audio/ep1.mp3"
    When I add the feed URL "/pod.xml"
    And I request the player of the item "Pod 1" via htmx
    Then the player plays the audio "<feed server>/audio/ep1.mp3"

  Scenario: An episode that fails to load offers its page instead
    Given the feed server serves "/pod.xml" as a podcast titled "Pod" whose episode has the audio enclosure "https://cdn.example/ep1.mp3"
    When I add the feed URL "/pod.xml"
    And I request the player of the item "Pod 1" via htmx
    Then the player holds a hidden notice "This episode can't be played here" linking to "<feed server>/pod.xml/item-1"

  Scenario: An episode without art of its own shows the show's art
    Given the feed server serves "/pod.xml" as a podcast titled "Pod" with the show art "https://cdn.example/show.jpg" whose episode has the audio enclosure "https://cdn.example/ep1.mp3"
    When I add the feed URL "/pod.xml"
    And I request the home page "/feed"
    Then the timeline shows "https://cdn.example/show.jpg" as the thumbnail of "Pod 1"
    When I request the player of the item "Pod 1" via htmx
    Then the player shows the art "https://cdn.example/show.jpg"

  Scenario: An episode is not opened in the reader
    Given the feed server serves "/pod.xml" as a podcast titled "Pod" whose episode has the audio enclosure "https://cdn.example/ep1.mp3"
    When I add the feed URL "/pod.xml"
    And I request the reader of the item "Pod 1" via htmx
    Then the response status is 404
    And the item "Pod 1" is unread

  Scenario: A SoundCloud track with an audio enclosure still plays in SoundCloud's widget
    Given the feed server serves "/sc.xml" as a podcast titled "Sound" whose episode links to "https://soundcloud.com/artist/track" and has the audio enclosure "https://cdn.example/track.mp3"
    When I add the feed URL "/sc.xml"
    And I request the player of the item "Sound 1" via htmx
    Then the response contains an iframe whose source is "https://w.soundcloud.com/player/?url=https%3A%2F%2Fsoundcloud.com%2Fartist%2Ftrack&auto_play=true"
    And the response contains no audio player

  Scenario: An entry with only an image enclosure is not an episode
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the image enclosure "https://cdn.example/pic.jpg"
    When I add the feed URL "/blog.xml"
    And I request the home page "/feed"
    Then the timeline entry "Blog 1" has no Play control

# Non-goals:
#   - Remembering positions, speed, downloads, chapters, video enclosures.
#
# Risks:
#   - Autoplay of the <audio> element can be refused until the listener taps:
#     the deck then stays paused and its Play starts it (playtest).
#   - Error display and the plain progress line are drawn by deck.js and CSS;
#     the scenarios pin the markup, the playtest the behaviour.
#
# Open questions:
#   - None (show art for episodes without their own, the link-out target and
#     the length formats are recorded as auto-decided in the intent doc).
