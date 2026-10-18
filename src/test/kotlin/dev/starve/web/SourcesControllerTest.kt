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
import dev.starve.application.OTHER_OWNER
import dev.starve.application.OWNER
import dev.starve.application.SubscriptionService
import dev.starve.application.quietFavicons
import dev.starve.domain.Feed
import dev.starve.domain.Group
import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
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
            Clock.systemUTC(),
        )

    @Test
    fun `lists each source with its formatted fetch time and error`() {
        val model = ConcurrentModel()

        val view = controller.sources(OWNER, model)

        assertEquals("sources", view)
        val views = (model.getAttribute("sources") as List<*>).map { it as SourceView }
        assertEquals(listOf(4L, 5L), views.map { it.id })
        assertEquals(listOf("Title", "Other"), views.map { it.title })
        assertEquals(listOf("http://a.test/feed", "http://b.test/feed"), views.map { it.url })
        assertEquals(listOf("2026-10-11 12:30:45 UTC", null), views.map { it.lastFetched?.full })
        assertEquals(listOf("oops", null), views.map { it.error })
        assertEquals(null, model.getAttribute("message"))
    }

    @Test
    fun `subscribing reports the new title`() {
        val model = ConcurrentModel()

        val view = controller.add(OWNER, "http://c.test/feed", model)

        assertEquals("sources", view)
        assertEquals("Subscribed to New", model.getAttribute("message"))
    }

    @Test
    fun `an invalid url is reported without subscribing`() {
        val model = ConcurrentModel()

        val view = controller.add(OWNER, "ftp://c.test/feed", model)

        assertEquals("sources", view)
        assertEquals("The feed URL must be an http or https URL", model.getAttribute("addError"))
        assertEquals("ftp://c.test/feed", model.getAttribute("url"))
        assertEquals(null, model.getAttribute("message"))
    }

    @Test
    fun `a url already followed is reported rather than subscribed twice`() {
        val model = ConcurrentModel()

        controller.add(OWNER, "http://a.test/feed", model)

        assertEquals("You are already subscribed to that feed", model.getAttribute("addError"))
    }

    @Test
    fun `a subscription is a status, and the roster names the row it is confirming`() {
        val added = ConcurrentModel().also { controller.add(OWNER, "http://c.test/feed", it) }
        assertEquals(false, added.getAttribute("error"))
        assertEquals(null, added.getAttribute("addError"))

        val model = ConcurrentModel()
        controller.sources(OWNER, model, remove = 4, deleteGroup = 1)

        assertEquals(4L, model.getAttribute("removing"))
        assertEquals(1L, model.getAttribute("deletingGroup"))
    }

    @Test
    fun `removing a source unsubscribes it and redirects to the list`() {
        val redirect = controller.remove(OWNER, 4)

        assertEquals("redirect:/sources", redirect)
        assertEquals(listOf(5L), sources.findAll().map { it.id })
    }

    @Test
    fun `creating a group redirects, and invalid names re-render the page with a message`() {
        assertEquals("redirect:/sources", controller.createGroup(OWNER, "News", ConcurrentModel()))
        val messages =
            listOf("  ", "x".repeat(51), "music").map {
                ConcurrentModel().also { model -> assertEquals("sources", controller.createGroup(OWNER, it, model)) }
            }

        assertEquals(
            listOf("group name is required", "at most 50 characters", "a group with that name already exists"),
            messages.map { it.getAttribute("message") },
        )
        assertEquals(listOf(true, true, true), messages.map { it.getAttribute("error") })
        assertEquals(listOf("Music", "News"), groups.findAll(OWNER).map { it.name })
    }

    @Test
    fun `renaming redirects, rejects clashes and reports an unknown group as not found`() {
        assertEquals("redirect:/sources", controller.renameGroup(OWNER, 1, "Tunes", ConcurrentModel()))
        assertEquals("redirect:/sources", controller.createGroup(OWNER, "News", ConcurrentModel()))
        val model = ConcurrentModel()

        controller.renameGroup(OWNER, 2, "tunes", model)

        assertEquals("a group with that name already exists", model.getAttribute("message"))
        assertThrows(ResponseStatusException::class.java) { controller.renameGroup(OWNER, 9, "x", ConcurrentModel()) }
    }

    @Test
    fun `deleting a group redirects to the list`() {
        assertEquals("redirect:/sources", controller.deleteGroup(OWNER, 1))
        assertEquals(listOf(1L), groups.deleted)
    }

    @Test
    fun `assigning a source sets its group, blank means ungrouped and unknown groups are not found`() {
        assertEquals("redirect:/sources", controller.assignGroup(OWNER, 4, "1"))
        assertEquals("redirect:/sources", controller.assignGroup(OWNER, 4, ""))
        assertEquals("redirect:/sources", controller.assignGroup(OWNER, 5, null))
        assertEquals(listOf<Pair<Long, Long?>>(4L to 1L, 4L to null, 5L to null), sources.assignments)
        assertThrows(ResponseStatusException::class.java) { controller.assignGroup(OWNER, 4, "9") }
        assertThrows(ResponseStatusException::class.java) { controller.assignGroup(OWNER, 4, "abc") }
    }

    @Test
    fun `another user's source and groups are not found, and nothing of theirs is listed`() {
        val model = ConcurrentModel()

        controller.sources(OTHER_OWNER, model)

        assertEquals(emptyList<Any>(), model.getAttribute("sources"))
        assertEquals(emptyList<Any>(), model.getAttribute("groups"))
        assertNotFound { controller.remove(OTHER_OWNER, 4) }
        assertNotFound { controller.deleteGroup(OTHER_OWNER, 1) }
        assertNotFound { controller.renameGroup(OTHER_OWNER, 1, "Tunes", ConcurrentModel()) }
        assertNotFound { controller.renameGroup(OTHER_OWNER, 1, null, ConcurrentModel()) }
        assertNotFound { controller.assignGroup(OTHER_OWNER, 4, "1") }
        assertNotFound { controller.assignGroup(OTHER_OWNER, 4, null) }
        assertEquals(listOf(4L, 5L), sources.findAll().map { it.id })
        assertEquals(listOf("Music"), groups.findAll(OWNER).map { it.name })
    }

    @Test
    fun `a rename without a name asks for one`() {
        val model = ConcurrentModel()

        assertEquals("sources", controller.renameGroup(OWNER, 1, null, model))

        assertEquals("group name is required", model.getAttribute("message"))
    }

    @Test
    fun `removing or deleting what does not exist is not found`() {
        assertNotFound { controller.remove(OWNER, 99) }
        assertNotFound { controller.deleteGroup(OWNER, 99) }
        assertNotFound { controller.assignGroup(OWNER, 99, null) }
    }

    private fun assertNotFound(action: () -> Unit) {
        val failure = assertThrows(ResponseStatusException::class.java) { action() }
        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }
}
