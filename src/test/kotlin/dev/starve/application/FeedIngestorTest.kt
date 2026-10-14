package dev.starve.application

import dev.starve.domain.Feed
import dev.starve.domain.FeedItem
import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FeedIngestorTest {
    private val now = Instant.parse("2026-10-11T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val source = Source(7, "http://a.test/feed", "Stored", Validators("e", "m"), null, null)
    private val sources = MemorySources()
    private val items = MemoryItems()
    private val ingestor = FeedIngestor(sources, items, clock, quietFavicons(sources))

    @Test
    fun `not modified records success keeping the stored title and validators`() {
        ingestor.apply(source, FetchResult.NotModified)

        assertEquals(listOf(MemorySources.Success(7, "Stored", Validators("e", "m"), now)), sources.successes)
    }

    @Test
    fun `a fetched feed records its own title and the new validators`() {
        ingestor.apply(source, FetchResult.Fetched(Feed("Fresh", emptyList()), Validators("e2", null)))

        assertEquals(listOf(MemorySources.Success(7, "Fresh", Validators("e2", null), now)), sources.successes)
    }

    @Test
    fun `a fetched feed without a usable title keeps the stored title`() {
        ingestor.apply(source, FetchResult.Fetched(Feed("  ", emptyList()), Validators()))

        assertEquals("Stored", sources.successes.single().title)
    }

    @Test
    fun `undated items are stamped with now and dated items keep their date`() {
        val dated = Instant.parse("2026-01-01T00:00:00Z")

        ingestor.apply(source, FetchResult.Fetched(Feed("F", listOf(item("a", null), item("b", dated))), Validators()))

        assertEquals(listOf(now, dated), items.inserted.map { it.publishedAt })
    }

    @Test
    fun `a failure is recorded against the source`() {
        ingestor.failed(source, "boom")

        assertEquals(listOf(7L to "boom"), sources.failures)
    }

    @Test
    fun `a fetched feed has its site icon looked up from its site link`() {
        val finder = RecordingFinder("https://cdn.test/i.png")
        val withIcons = FeedIngestor(sources, items, clock, FaviconService(sources, finder))

        withIcons.apply(source, FetchResult.Fetched(Feed("F", emptyList(), "https://site.test/blog"), Validators()))

        assertEquals(listOf("https://site.test/blog"), finder.asked)
        assertEquals(listOf(Triple(7L, "https://cdn.test/i.png", now)), sources.favicons)
    }

    @Test
    fun `a not-modified poll looks up the icon from the feed origin`() {
        val finder = RecordingFinder(null)

        FeedIngestor(sources, items, clock, FaviconService(sources, finder)).apply(source, FetchResult.NotModified)

        assertEquals(listOf("http://a.test/"), finder.asked)
        assertEquals(listOf(Triple(7L, null, now)), sources.favicons)
    }

    private class RecordingFinder(
        private val icon: String?,
    ) : FaviconFinder {
        val asked = mutableListOf<String>()

        override fun find(siteUrl: String): String? {
            asked += siteUrl
            return icon
        }
    }

    private fun item(
        key: String,
        publishedAt: Instant?,
    ) = FeedItem(key, key, null, publishedAt, null, null)
}
