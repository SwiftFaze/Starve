package dev.starve.web

import dev.starve.application.FeedFetcher
import dev.starve.application.FeedIngestor
import dev.starve.application.FeedLocator
import dev.starve.application.FetchResult
import dev.starve.application.GroupService
import dev.starve.application.LocateResult
import dev.starve.application.MemoryGroups
import dev.starve.application.MemoryItems
import dev.starve.application.MemorySources
import dev.starve.application.SubscriptionService
import dev.starve.application.quietFavicons
import dev.starve.domain.Feed
import dev.starve.domain.Group
import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.ui.ConcurrentModel
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant

class SourcesControllerTest {
    private val stored =
        mutableListOf(
            Source(4, "http://a.test/feed", "Title", Validators(), Instant.parse("2026-10-11T12:30:45Z"), "oops"),
            Source(5, "http://b.test/feed", "Other", Validators(), null, null),
        )
    private val sources = MemorySources(stored)
    private val groups = MemoryGroups(mutableListOf(Group(1, "Music")))
    private val fetcher =
        object : FeedFetcher {
            override fun fetch(
                url: String,
                validators: Validators,
            ) = FetchResult.Fetched(Feed("New", emptyList()), Validators())
        }
    private val locator =
        object : FeedLocator {
            override fun locate(url: String) = LocateResult.Found(url)
        }
    private val ingestor = FeedIngestor(sources, MemoryItems(), Clock.systemUTC(), quietFavicons(sources))
    private val controller =
        SourcesController(
            SubscriptionService(sources, locator, fetcher, ingestor),
            GroupService(groups, sources),
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

    @Test
    fun `creating a group redirects, and invalid names re-render the page with a message`() {
        assertEquals("redirect:/sources", controller.createGroup("News", ConcurrentModel()))
        val messages =
            listOf("  ", "x".repeat(51), "music").map {
                ConcurrentModel().also { model -> assertEquals("sources", controller.createGroup(it, model)) }
            }

        assertEquals(
            listOf("group name is required", "at most 50 characters", "a group with that name already exists"),
            messages.map { it.getAttribute("message") },
        )
        assertEquals(listOf("Music", "News"), groups.findAll().map { it.name })
    }

    @Test
    fun `renaming redirects, rejects clashes and reports an unknown group as not found`() {
        assertEquals("redirect:/sources", controller.renameGroup(1, "Tunes", ConcurrentModel()))
        assertEquals("redirect:/sources", controller.createGroup("News", ConcurrentModel()))
        val model = ConcurrentModel()

        controller.renameGroup(2, "tunes", model)

        assertEquals("a group with that name already exists", model.getAttribute("message"))
        assertThrows(ResponseStatusException::class.java) { controller.renameGroup(9, "x", ConcurrentModel()) }
    }

    @Test
    fun `deleting a group redirects to the list`() {
        assertEquals("redirect:/sources", controller.deleteGroup(1))
        assertEquals(listOf(1L), groups.deleted)
    }

    @Test
    fun `assigning a source sets its group, blank means ungrouped and unknown groups are not found`() {
        assertEquals("redirect:/sources", controller.assignGroup(4, "1"))
        assertEquals("redirect:/sources", controller.assignGroup(4, ""))
        assertEquals("redirect:/sources", controller.assignGroup(5, null))
        assertEquals(listOf<Pair<Long, Long?>>(4L to 1L, 4L to null, 5L to null), sources.assignments)
        assertThrows(ResponseStatusException::class.java) { controller.assignGroup(4, "9") }
        assertThrows(ResponseStatusException::class.java) { controller.assignGroup(4, "abc") }
    }
}
