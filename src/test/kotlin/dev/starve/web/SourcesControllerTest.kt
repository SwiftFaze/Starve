package dev.starve.web

import dev.starve.application.FeedFetcher
import dev.starve.application.FeedIngestor
import dev.starve.application.FetchResult
import dev.starve.application.MemoryItems
import dev.starve.application.MemorySources
import dev.starve.application.SubscriptionService
import dev.starve.domain.Feed
import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.ui.ConcurrentModel
import java.time.Clock
import java.time.Instant

class SourcesControllerTest {
    private val stored =
        mutableListOf(
            Source(4, "http://a.test/feed", "Title", Validators(), Instant.parse("2026-10-11T12:30:45Z"), "oops"),
            Source(5, "http://b.test/feed", "Other", Validators(), null, null),
        )
    private val sources = MemorySources(stored)
    private val fetcher =
        object : FeedFetcher {
            override fun fetch(
                url: String,
                validators: Validators,
            ) = FetchResult.Fetched(Feed("New", emptyList()), Validators())
        }
    private val controller =
        SourcesController(
            SubscriptionService(sources, fetcher, FeedIngestor(sources, MemoryItems(), Clock.systemUTC())),
        )

    @Test
    fun `lists each source with its formatted fetch time and error`() {
        val model = ConcurrentModel()

        val view = controller.sources(model)

        assertEquals("sources", view)
        val views = (model.getAttribute("sources") as List<*>).map { it as SourceView }
        assertEquals(listOf(4L, 5L), views.map { it.id })
        assertEquals(listOf("Title", "Other"), views.map { it.title })
        assertEquals(listOf("http://a.test/feed", "http://b.test/feed"), views.map { it.url })
        assertEquals(listOf("2026-10-11 12:30:45 UTC", null), views.map { it.lastFetched })
        assertEquals(listOf("oops", null), views.map { it.error })
        assertEquals(null, model.getAttribute("message"))
    }

    @Test
    fun `subscribing reports the new title`() {
        val model = ConcurrentModel()

        controller.add("http://c.test/feed", model)

        assertEquals("Subscribed to New", model.getAttribute("message"))
    }

    @Test
    fun `an invalid url is reported without subscribing`() {
        val model = ConcurrentModel()

        controller.add("ftp://c.test/feed", model)

        assertEquals("The feed URL must be an http or https URL", model.getAttribute("message"))
    }

    @Test
    fun `removing a source unsubscribes it and redirects to the list`() {
        val redirect = controller.remove(4)

        assertEquals("redirect:/sources", redirect)
        assertEquals(listOf(5L), sources.findAll().map { it.id })
    }
}
