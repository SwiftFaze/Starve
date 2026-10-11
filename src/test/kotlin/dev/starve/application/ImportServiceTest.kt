package dev.starve.application

import dev.starve.domain.Feed
import dev.starve.domain.Group
import dev.starve.domain.ImportEntry
import dev.starve.domain.ParsedImport
import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock

class ImportServiceTest {
    private val idA = "UC" + "a".repeat(22)
    private val csv = "Channel Id,Channel Title\n$idA,Alpha\n"
    private val stored = mutableListOf<Source>()
    private val memory = MemorySources(stored)
    private val sources = LookupSources(memory)
    private val groups = MemoryGroups(mutableListOf(Group(1, "news")))
    private val fetched = mutableListOf<String>()
    private val queued = mutableListOf<Runnable>()
    private var opmlResult: ParsedImport? = null
    private val fetcher =
        object : FeedFetcher {
            override fun fetch(
                url: String,
                validators: Validators,
            ): FetchResult {
                fetched += url
                return FetchResult.Fetched(Feed("t", emptyList()), Validators())
            }
        }
    private val ingestor = FeedIngestor(sources, MemoryItems(), Clock.systemUTC())
    private val service =
        ImportService(
            sources,
            groups,
            { _ -> opmlResult },
            BackgroundPoller(sources, PollService(sources, fetcher, ingestor)) { queued += it },
            "https://yt.test/{id}",
        )

    private fun import(text: String) = service.importFile(text.toByteArray())

    private fun summary(outcome: ImportOutcome) = (outcome as ImportOutcome.Done).summary

    private fun opml(vararg entries: ImportEntry) {
        opmlResult = ParsedImport(entries.toList(), listOf("outline 'x': not an http(s) URL"))
    }

    @Test
    fun `a takeout csv adds its channels with the csv title and polls them only in the background`() {
        val summary = summary(import("\uFEFF$csv"))

        assertEquals(ImportSummary(1, 0, emptyList()), summary)
        assertEquals(listOf("https://yt.test/$idA" to "Alpha"), stored.map { it.url to it.title })
        assertEquals(emptyList<String>(), fetched)
        assertEquals(1, queued.size)

        queued.single().run()

        assertEquals(listOf("https://yt.test/$idA"), fetched)
    }

    @Test
    fun `importing the same csv again adds nothing and polls nothing`() {
        import(csv)
        queued.clear()

        val summary = summary(import(csv))

        assertEquals(ImportSummary(0, 1, emptyList()), summary)
        assertEquals(1, stored.size)
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `content that is neither a takeout csv nor opml is unrecognised`() {
        assertEquals(ImportOutcome.Unrecognised, import("just text"))
        opmlResult = null
        assertEquals(ImportOutcome.Unrecognised, import("  <rss/>"))
        assertTrue(stored.isEmpty())
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `opml folders are assigned to groups matched ignoring case and created when missing`() {
        opml(
            ImportEntry("http://a.test/f", "A", "News"),
            ImportEntry("http://b.test/f", "B", "Music"),
            ImportEntry("http://c.test/f", "C", "music"),
            ImportEntry("http://d.test/f", "D", null),
            ImportEntry("http://e.test/f", "E", "y".repeat(60)),
        )

        val summary = summary(import("<opml/>"))

        assertEquals(ImportSummary(5, 0, listOf("outline 'x': not an http(s) URL")), summary)
        assertEquals(1, summary.failed)
        assertEquals(listOf("Music", "news", "y".repeat(50)), groups.findAll().map { it.name })
        assertEquals(listOf(1L to 1L, 2L to 2L, 3L to 2L, 5L to 3L), memory.assignments)
    }

    @Test
    fun `an already subscribed feed is grouped only when it has no group`() {
        stored += Source(1, "http://a.test/f", "A", Validators(), null, null)
        stored += Source(2, "http://b.test/f", "B", Validators(), null, null, groupId = 7)
        opml(ImportEntry("http://a.test/f", "A", "News"), ImportEntry("http://b.test/f", "B", "News"))

        val summary = summary(import("<opml/>"))

        assertEquals(0, summary.added)
        assertEquals(2, summary.alreadySubscribed)
        assertEquals(listOf(1L to 1L), memory.assignments)
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `a blank folder name leaves the feed ungrouped`() {
        opml(ImportEntry("http://a.test/f", "A", "   "))

        import("<opml/>")

        assertTrue(memory.assignments.isEmpty())
        assertEquals(1, groups.findAll().size)
    }

    @Test
    fun `a feed repeated within one file counts once as added and once as already subscribed`() {
        opml(ImportEntry("http://a.test/f", "A", "News"), ImportEntry("http://a.test/f", "A", "Other"))

        val summary = summary(import("<opml/>"))

        assertEquals(1, summary.added)
        assertEquals(1, summary.alreadySubscribed)
        assertEquals(listOf(1L to 1L), memory.assignments)
    }

    /** The memory store, but able to look sources up by id as the real store does. */
    private class LookupSources(
        private val delegate: MemorySources,
    ) : SourceRepository by delegate {
        override fun findById(id: Long): Source? = delegate.findAll().firstOrNull { it.id == id }
    }
}
