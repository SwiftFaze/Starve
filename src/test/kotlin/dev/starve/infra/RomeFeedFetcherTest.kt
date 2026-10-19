package dev.starve.infra

import dev.starve.acceptance.FeedServer
import dev.starve.acceptance.FeedSpec
import dev.starve.acceptance.MediaContent
import dev.starve.acceptance.Route
import dev.starve.application.FeedFetchException
import dev.starve.application.FeedFetcher
import dev.starve.application.FeedIngestor
import dev.starve.application.FetchResult
import dev.starve.application.FetchSuccess
import dev.starve.application.InvalidFeedException
import dev.starve.application.PollService
import dev.starve.application.SourceRepository
import dev.starve.application.quietFavicons
import dev.starve.domain.AudioEnclosure
import dev.starve.domain.FeedItem
import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import dev.starve.application.MemoryItems as BaseMemoryItems

class RomeFeedFetcherTest {
    private lateinit var server: FeedServer
    private lateinit var fetcher: FeedFetcher

    @BeforeEach
    fun setUp() {
        server = FeedServer()
        fetcher = fetcherWithTimeout(GENEROUS_TIMEOUT)
    }

    @AfterEach
    fun tearDown() = server.stop()

    @Test
    fun `a body that stalls after the headers times out as a fetch error`() {
        server.route("/slow.xml", Route.StallBody)
        val started = System.nanoTime()
        val fetcher = fetcherWithTimeout(Duration.ofMillis(500))
        assertThrows(FeedFetchException::class.java) { fetcher.fetch("${server.baseUrl}/slow.xml", Validators()) }
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(3))
    }

    @Test
    fun `a stalled source records an error and does not stop other sources polling`() {
        server.route("/slow.xml", Route.StallBody)
        server.route("/ok.xml", Route.Feed(FeedSpec(title = "Ok", entries = 2)))
        val repo = MemorySources(listOf("${server.baseUrl}/slow.xml", "${server.baseUrl}/ok.xml"))
        val items = MemoryItems()
        val ingestor = FeedIngestor(repo, items, Clock.systemUTC(), quietFavicons(repo))

        PollService(repo, fetcherWithTimeout(Duration.ofSeconds(3)), ingestor).pollDue(Instant.now(), Duration.ZERO)

        assertTrue(repo.errors[1L]?.isNotBlank() == true)
        assertEquals(null, repo.errors[2L])
        assertEquals(2, items.stored)
    }

    @Test
    fun `fetch returns the parsed feed when the server answers in time`() {
        server.route("/ok.xml", Route.Feed(FeedSpec(title = "Ok", entries = 1)))
        val result = fetcher.fetch("${server.baseUrl}/ok.xml", Validators()) as FetchResult.Fetched
        assertEquals("Ok", result.feed.title)
    }

    @Test
    fun `entries are mapped with trimmed titles, update dates, authors and thumbnails`() {
        server.route("/rich.xml", Route.Raw("atom-rich.xml", "application/atom+xml"))

        val feed = (fetcher.fetch("${server.baseUrl}/rich.xml", Validators()) as FetchResult.Fetched).feed

        assertEquals("Spaced Title", feed.title)
        val (first, second, third) = feed.items
        assertEquals("First", first.title)
        assertEquals("http://example.test/1", first.link)
        assertEquals(Instant.parse("2026-01-02T10:00:00Z"), first.publishedAt)
        assertEquals("Ann", first.author)
        assertEquals("http://example.test/t1.jpg", first.thumbnailUrl)
        assertEquals("(untitled)", second.title)
        assertEquals(null, second.link)
        assertEquals(null, second.author)
        assertEquals(null, second.thumbnailUrl)
        assertEquals("http://example.test/e3.png", third.thumbnailUrl)
    }

    @Test
    fun `a per-item itunes image becomes the thumbnail and the channel image is ignored`() {
        server.route("/itunes.xml", Route.Raw("rss-itunes.xml", "application/rss+xml"))

        val feed = (fetcher.fetch("${server.baseUrl}/itunes.xml", Validators()) as FetchResult.Fetched).feed

        val (first, second) = feed.items
        assertEquals("https://i1.sndcdn.com/artworks-one-t500x500.jpg", first.thumbnailUrl)
        assertEquals(null, second.thumbnailUrl)
    }

    @Test
    fun `an entity-encoded title is decoded and trimmed after decoding`() {
        assertEquals("Tom & Jerry", titleFetchedFrom("&amp;#32;Tom &amp;amp; Jerry&amp;#32;"))
    }

    @Test
    fun `a title that decodes to whitespace is untitled`() {
        assertEquals("(untitled)", titleFetchedFrom("&amp;#32;"))
    }

    @Test
    fun `an untitled entry is titled from the start of its body text`() {
        val description = "<p>Happy <b>opening</b> day! [contains quote post or other embedded content]</p>"
        server.route("/t.xml", Route.Feed(FeedSpec(title = "Ok", itemTitle = "", description = description)))

        val item = (fetcher.fetch("${server.baseUrl}/t.xml", Validators()) as FetchResult.Fetched).feed.items.single()

        assertEquals("Happy opening day!", item.title)
    }

    @Test
    fun `a titled entry keeps its title whatever its body`() {
        server.route("/t.xml", Route.Feed(FeedSpec(title = "Ok", itemTitle = "Real", description = "<p>Body</p>")))

        val item = (fetcher.fetch("${server.baseUrl}/t.xml", Validators()) as FetchResult.Fetched).feed.items.single()

        assertEquals("Real", item.title)
    }

    @Test
    fun `a permanent redirect is followed and reported as the move`() {
        server.route("/new.xml", Route.Feed(FeedSpec(title = "New", entries = 1)))
        for (code in listOf(301, 308)) {
            server.route("/old.xml", Route.Redirect(code, "${server.baseUrl}/new.xml"))
            val result = fetcher.fetch("${server.baseUrl}/old.xml", Validators()) as FetchResult.Fetched
            assertEquals("New", result.feed.title)
            assertEquals("${server.baseUrl}/new.xml", result.movedTo, "HTTP $code")
        }
    }

    @Test
    fun `a temporary redirect is followed without a move`() {
        server.route("/new.xml", Route.Feed(FeedSpec(title = "New", entries = 1)))
        for (code in listOf(302, 303, 307)) {
            server.route("/old.xml", Route.Redirect(code, "/new.xml"))
            val result = fetcher.fetch("${server.baseUrl}/old.xml", Validators()) as FetchResult.Fetched
            assertEquals("New", result.feed.title)
            assertEquals(null, result.movedTo, "HTTP $code")
        }
    }

    @Test
    fun `a plain fetch reports no move`() {
        server.route("/ok.xml", Route.Feed(FeedSpec(title = "Ok", entries = 1)))
        assertEquals(null, fetcher.fetch("${server.baseUrl}/ok.xml", Validators()).movedTo)
    }

    @Test
    fun `only the leading run of permanent redirects is the move, resolved against the URL that answered`() {
        server.route("/a.xml", Route.Redirect(301, "/b.xml"))
        server.route("/b.xml", Route.Redirect(308, "c.xml"))
        server.route("/c.xml", Route.Redirect(302, "/d.xml"))
        server.route("/d.xml", Route.Redirect(301, "/e.xml"))
        server.route("/e.xml", Route.Feed(FeedSpec(title = "End", entries = 1)))
        assertEquals("${server.baseUrl}/c.xml", fetcher.fetch("${server.baseUrl}/a.xml", Validators()).movedTo)

        server.route("/t.xml", Route.Redirect(307, "/d.xml"))
        assertEquals(null, fetcher.fetch("${server.baseUrl}/t.xml", Validators()).movedTo)
    }

    @Test
    fun `a moved feed that is not modified reports the move with the conditional answer`() {
        server.route("/new.xml", Route.Feed(FeedSpec(title = "New", entries = 1, etag = true)))
        server.route("/old.xml", Route.Redirect(301, "/new.xml"))
        val result = fetcher.fetch("${server.baseUrl}/old.xml", Validators(etag = FeedServer.ETAG))
        assertEquals(FetchResult.NotModified("${server.baseUrl}/new.xml"), result)
        assertTrue(server.requests.all { it.ifNoneMatch == FeedServer.ETAG })
    }

    @Test
    fun `a redirect loop is a fetch failure`() {
        server.route("/a.xml", Route.Redirect(301, "/b.xml"))
        server.route("/b.xml", Route.Redirect(302, "/a.xml"))
        assertEquals("redirect loop", failureOf("/a.xml"))
    }

    @Test
    fun `five redirects are followed and a sixth is a fetch failure`() {
        (0 until 6).forEach { server.route("/r$it.xml", Route.Redirect(302, "/r${it + 1}.xml")) }
        server.route("/r6.xml", Route.Feed(FeedSpec(title = "Far", entries = 1)))
        val far = fetcher.fetch("${server.baseUrl}/r1.xml", Validators()) as FetchResult.Fetched
        assertEquals("Far", far.feed.title)

        assertEquals("more than 5 redirects", failureOf("/r0.xml"))
    }

    @Test
    fun `a redirect without a location is an HTTP failure`() {
        server.route("/a.xml", Route.Redirect(301, null))
        assertEquals("HTTP 301", failureOf("/a.xml"))
    }

    @Test
    fun `a redirect to an unusable address is a fetch failure`() {
        server.route("/ftp.xml", Route.Redirect(301, "ftp://files.test/feed.xml"))
        server.route("/bad.xml", Route.Redirect(301, "http://bad host/feed.xml"))
        assertEquals("redirect to a non-HTTP address", failureOf("/ftp.xml"))
        assertEquals("redirect to an invalid address", failureOf("/bad.xml"))
    }

    @Test
    fun `a redirect to a page that is not a feed is a parse failure`() {
        server.route("/old.xml", Route.Redirect(301, "/page.html"))
        server.route("/page.html", Route.Raw("page.html", "text/html"))
        assertThrows(InvalidFeedException::class.java) { fetcher.fetch("${server.baseUrl}/old.xml", Validators()) }
    }

    private fun failureOf(path: String): String? =
        assertThrows(FeedFetchException::class.java) { fetcher.fetch("${server.baseUrl}$path", Validators()) }.message

    // Only the stall tests need a short timeout; a short one elsewhere fails on a loaded machine.
    private fun fetcherWithTimeout(timeout: Duration): FeedFetcher =
        RomeFeedFetcher(BoundedHttp(HttpClient.newHttpClient(), timeout, "test"))

    private fun titleFetchedFrom(rawTitle: String): String {
        val spec = FeedSpec(title = "Ok", entries = 1, itemTitle = rawTitle)
        server.route("/t.xml", Route.Feed(spec))
        val result = fetcher.fetch("${server.baseUrl}/t.xml", Validators()) as FetchResult.Fetched
        return result.feed.items
            .single()
            .title
    }

    @Test
    fun `the body prefers the content element over the description`() {
        assertEquals("<p>Full</p>", bodyOf(FeedSpec(description = "<p>Teaser</p>", content = "<p>Full</p>")))
    }

    @Test
    fun `the body falls back to the description`() {
        assertEquals("<p>Teaser</p>", bodyOf(FeedSpec(description = "<p>Teaser</p>")))
    }

    @Test
    fun `there is no body when the entry has neither or only unsafe markup`() {
        assertEquals(null, bodyOf(FeedSpec()))
        assertEquals(null, bodyOf(FeedSpec(content = "<script>x()</script>")))
    }

    @Test
    fun `an atom entry body is read from its content`() {
        assertEquals("<p>Atom</p>", bodyOf(FeedSpec(atom = true, content = "<p>Atom</p>")))
    }

    @Test
    fun `body links are resolved against the item link`() {
        val body = bodyOf(FeedSpec(content = "<a href=\"/x\">x</a>"))!!

        assertTrue(body.contains("href=\"${server.baseUrl}/x\""))
    }

    private fun bodyOf(spec: FeedSpec): String? {
        server.route("/b.xml", Route.Feed(spec))
        val result = fetcher.fetch("${server.baseUrl}/b.xml", Validators()) as FetchResult.Fetched
        return result.feed.items
            .single()
            .body
    }

    @Test
    fun `the feed carries its trimmed site link, and none when the channel has no link`() {
        assertEquals("https://blog.example/", feedOf(FeedSpec(siteLink = " https://blog.example/ ")).siteLink)
        assertEquals(null, feedOf(FeedSpec(siteLink = "  ")).siteLink)
        assertEquals(null, feedOf(FeedSpec()).siteLink)
    }

    @Test
    fun `media thumbnail wins over every other image source`() {
        val spec =
            FeedSpec(
                mediaThumbnail = "https://img.example/thumb.png",
                mediaContent = MediaContent("https://img.example/content.png", type = "image/png"),
                enclosure = "https://img.example/enclosure.png",
                coverArt = true,
                coverArtUrl = "https://img.example/cover.png",
                content = "<img src=\"https://img.example/body.png\">",
            )

        assertEquals("https://img.example/thumb.png", thumbnailOf(spec))
    }

    @Test
    fun `a media content image by type or by medium beats the enclosure, and a video does not count`() {
        val enclosure = "https://img.example/enclosure.png"
        val byType = MediaContent("https://img.example/type.png", type = "image/png")
        val byMedium = MediaContent("https://img.example/medium.png", medium = "image")
        val video = MediaContent("https://img.example/clip.mp4", type = "video/mp4", medium = "video")

        val withEnclosure = { m: MediaContent -> thumbnailOf(FeedSpec(mediaContent = m, enclosure = enclosure)) }

        assertEquals("https://img.example/type.png", withEnclosure(byType))
        assertEquals("https://img.example/medium.png", withEnclosure(byMedium))
        assertEquals(enclosure, withEnclosure(video))
    }

    @Test
    fun `an untyped media content is not an image`() {
        val untyped = MediaContent("https://img.example/blob")

        assertEquals(null, thumbnailOf(FeedSpec(mediaContent = untyped)))
    }

    @Test
    fun `the enclosure beats the itunes image, which beats a body image`() {
        val body = "<img src=\"https://img.example/body.png\">"
        val cover = "https://img.example/cover.png"

        val enclosure = "https://img.example/enclosure.png"

        assertEquals(enclosure, thumbnailOf(FeedSpec(enclosure = enclosure, coverArt = true, coverArtUrl = cover)))
        assertEquals(cover, thumbnailOf(FeedSpec(coverArt = true, coverArtUrl = cover, content = body)))
        assertEquals("https://img.example/body.png", thumbnailOf(FeedSpec(content = body)))
    }

    @Test
    fun `a relative image resolves against the item link, else the site link`() {
        val relative = "<img src=\"pic.png\">"

        val site = "https://site.example/"

        assertEquals(
            "https://post.example/a/pic.png",
            thumbnailOf(FeedSpec(entryLink = "https://post.example/a/b", siteLink = site, content = relative)),
        )
        assertEquals(
            "https://site.example/pic.png",
            thumbnailOf(FeedSpec(linkless = true, siteLink = site, content = relative)),
        )
    }

    private fun feedOf(spec: FeedSpec): dev.starve.domain.Feed {
        server.route("/f.xml", Route.Feed(spec))
        return (fetcher.fetch("${server.baseUrl}/f.xml", Validators()) as FetchResult.Fetched).feed
    }

    private fun thumbnailOf(spec: FeedSpec): String? = feedOf(spec).items.single().thumbnailUrl

    @Test
    fun `an audio enclosure is the episode's audio, with its itunes duration`() {
        val spec = FeedSpec(audioEnclosure = "https://cdn.example/ep.mp3", itunesDuration = "1:02:03")
        val item = feedOf(spec).items.single()

        assertEquals(AudioEnclosure("https://cdn.example/ep.mp3", 3723), item.audio)
    }

    @Test
    fun `an audio media content is the episode's audio too, by type or by medium`() {
        val typed = MediaContent("https://cdn.example/ep.m4a", type = "audio/mp4")
        val medium = MediaContent("https://cdn.example/ep.ogg", medium = "audio")

        val audioOf = { content: MediaContent -> feedOf(FeedSpec(mediaContent = content)).items.single().audio }
        assertEquals(AudioEnclosure("https://cdn.example/ep.m4a"), audioOf(typed))
        assertEquals(AudioEnclosure("https://cdn.example/ep.ogg"), audioOf(medium))
    }

    @Test
    fun `a relative enclosure resolves against the entry's link, else the site's`() {
        val linked = feedOf(FeedSpec(audioEnclosure = "ep.mp3", entryLink = "https://show.example/eps/1"))
        val site = "https://site.example/a/"
        val linkless = feedOf(FeedSpec(audioEnclosure = "ep.mp3", linkless = true, siteLink = site))

        assertEquals(
            "https://show.example/eps/ep.mp3",
            linked.items
                .single()
                .audio
                ?.url,
        )
        assertEquals(
            "https://site.example/a/ep.mp3",
            linkless.items
                .single()
                .audio
                ?.url,
        )
    }

    @Test
    fun `an image enclosure or image media content is no audio`() {
        assertNull(feedOf(FeedSpec(enclosure = "https://cdn.example/a.jpg")).items.single().audio)
        val image = MediaContent("https://cdn.example/a.jpg", type = "image/jpeg")
        assertNull(feedOf(FeedSpec(mediaContent = image)).items.single().audio)
    }

    @Test
    fun `an episode without its own art takes the show's, and other entries never do`() {
        val art = "https://cdn.example/show.jpg"

        assertEquals(art, thumbnailOf(FeedSpec(audioEnclosure = "https://cdn.example/ep.mp3", showArt = art)))
        assertNull(thumbnailOf(FeedSpec(showArt = art)))
        val episode = FeedSpec(audioEnclosure = "https://cdn.example/ep.mp3", showArt = art)
        assertNull(thumbnailOf(episode.copy(entryLink = "https://soundcloud.com/a/b")))
        val own = "https://cdn.example/own.jpg"
        assertEquals(own, thumbnailOf(episode.copy(mediaThumbnail = own)))
    }

    @Test
    fun `blank titles and authors are treated as absent`() {
        server.route("/blank.xml", Route.Raw("rss-blank.xml", "application/rss+xml"))

        val feed = (fetcher.fetch("${server.baseUrl}/blank.xml", Validators()) as FetchResult.Fetched).feed

        assertEquals(null, feed.title)
        assertEquals("(untitled)", feed.items.single().title)
        assertEquals(null, feed.items.single().author)
    }

    @Test
    fun `any 2xx status is parsed as a feed and other statuses are fetch errors`() {
        server.route("/200", Route.Status(200))
        server.route("/299", Route.Status(299))
        server.route("/300", Route.Status(300))

        assertThrows(InvalidFeedException::class.java) { fetcher.fetch("${server.baseUrl}/200", Validators()) }
        assertThrows(InvalidFeedException::class.java) { fetcher.fetch("${server.baseUrl}/299", Validators()) }
        assertThrows(FeedFetchException::class.java) { fetcher.fetch("${server.baseUrl}/300", Validators()) }
    }

    @Test
    fun `an unreachable server is a fetch error with a message`() {
        val url = "${server.baseUrl}/gone.xml"
        server.stop()

        val error = assertThrows(FeedFetchException::class.java) { fetcher.fetch(url, Validators()) }

        assertTrue(error.message!!.isNotBlank())
    }

    @Test
    fun `an interrupted fetch fails and keeps the interrupt flag`() {
        server.route("/ok.xml", Route.Feed(FeedSpec()))
        Thread.currentThread().interrupt()

        val url = "${server.baseUrl}/ok.xml"

        val error = assertThrows(FeedFetchException::class.java) { fetcher.fetch(url, Validators()) }

        assertEquals("interrupted", error.message)
        assertTrue(Thread.interrupted())
    }

    private class MemorySources(
        urls: List<String>,
    ) : SourceRepository {
        private val all = urls.mapIndexed { i, u -> Source(i + 1L, u, u, Validators(), null, null) }
        val errors = mutableMapOf<Long, String?>()

        override fun findAll() = all

        override fun findById(id: Long) = all.firstOrNull { it.id == id }

        override fun findAllOf(owner: Long) = all

        override fun existsByUrl(
            owner: Long,
            url: String,
        ) = all.any { it.url == url }

        override fun insert(
            owner: Long,
            url: String,
            title: String,
        ): Source = error("unused")

        override fun delete(
            owner: Long,
            id: Long,
        ) = error("unused")

        override fun assignGroup(
            owner: Long,
            sourceId: Long,
            groupId: Long?,
        ) = error("unused")

        override fun recordSuccess(
            id: Long,
            success: FetchSuccess,
        ) {
            errors[id] = null
        }

        override fun recordFailure(
            id: Long,
            error: String,
            at: Instant,
        ) {
            errors[id] = error
        }

        override fun recordFavicon(
            id: Long,
            url: String?,
            at: Instant,
        ) {
            // Icons are not under test here.
        }
    }

    private class MemoryItems : BaseMemoryItems() {
        var stored = 0

        override fun insertNew(
            sourceId: Long,
            items: List<FeedItem>,
            firstSeenAt: Instant,
        ) {
            stored += items.size
        }
    }

    private companion object {
        val GENEROUS_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
