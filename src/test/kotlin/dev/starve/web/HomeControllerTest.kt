package dev.starve.web

import dev.starve.application.MemorySources
import dev.starve.application.TimelineItems
import dev.starve.application.TimelineService
import dev.starve.domain.Source
import dev.starve.domain.TimelineItem
import dev.starve.domain.TimelineRules
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.ui.ConcurrentModel
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HomeControllerTest {
    private val now = Instant.parse("2026-10-11T12:00:00Z")
    private val published = Instant.parse("2026-10-10T08:05:00Z")
    private val source = Source(1, "http://a.test/f", "A", Validators(), null, null)

    private fun controller(items: TimelineItems) =
        HomeController(TimelineService(items, MemorySources(mutableListOf(source)), Clock.fixed(now, ZoneOffset.UTC)))

    private fun row(id: Long) = TimelineItem(id, "A", "T$id", published, "http://img.test/$id", id == 1L)

    @Test
    fun `home renders formatted views and the render timestamp`() {
        val model = ConcurrentModel()

        val view = controller(TimelineItems(listOf(row(1)))).home(null, null, null, model)

        assertEquals("home", view)
        val item = (model.getAttribute("items") as List<*>).single() as TimelineItemView
        val expected =
            TimelineItemView(1, "A", "T1", "2026-10-10 08:05 UTC", published.toString(), "http://img.test/1", true)
        assertEquals(expected, item)
        assertEquals(1L, item.id)
        assertEquals("T1", item.title)
        assertEquals("2026-10-10 08:05 UTC", item.published)
        assertEquals(published.toString(), item.publishedIso)
        assertTrue(item.read)
        assertEquals(now.toEpochMilli(), model.getAttribute("renderedAt"))
        assertEquals(false, model.getAttribute("showAll"))
        assertEquals(true, model.getAttribute("hasSources"))
        assertNull(model.getAttribute("moreHref"))
    }

    @Test
    fun `an unread row is presented as unread`() {
        val model = ConcurrentModel()

        controller(TimelineItems(listOf(row(2)))).home(null, null, null, model)

        val item = (model.getAttribute("items") as List<*>).single() as TimelineItemView
        assertFalse(item.read)
    }

    @Test
    fun `show all is passed through and keeps the cursor link`() {
        val items = TimelineItems((1L..TimelineRules.PAGE_SIZE + 1).map { row(it) })
        val model = ConcurrentModel()

        controller(items).home("all", null, null, model)

        assertEquals(true, items.requested!!.second)
        assertEquals("/?after=${published.toEpochMilli()}_50&show=all", model.getAttribute("moreHref"))
    }

    @Test
    fun `an htmx request with a cursor returns only the fragment`() {
        val view = controller(TimelineItems()).home(null, "1_2", "true", ConcurrentModel())

        assertEquals("home :: more", view)
    }

    @Test
    fun `an htmx request without a cursor returns the full page`() {
        assertEquals("home", controller(TimelineItems()).home(null, null, "true", ConcurrentModel()))
    }

    @Test
    fun `opening redirects to the link or home when there is none`() {
        val c = controller(TimelineItems(links = mapOf(1L to "http://x.test/a", 2L to null)))

        assertEquals("redirect:http://x.test/a", c.open(1))
        assertEquals("redirect:/", c.open(2))
    }

    @Test
    fun `opening an unknown item is not found`() {
        val e = assertThrows(ResponseStatusException::class.java) { controller(TimelineItems()).open(9) }

        assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
    }

    @Test
    fun `marking read answers htmx with an empty body and others with a redirect`() {
        val c = controller(TimelineItems(links = mapOf(1L to null)))

        val htmx = c.markRead(1, "true") as ResponseEntity<*>

        assertEquals(HttpStatus.OK, htmx.statusCode)
        assertEquals("", htmx.body)
        assertEquals("redirect:/", c.markRead(1, null))
    }

    @Test
    fun `marking an unknown item read is not found`() {
        val e = assertThrows(ResponseStatusException::class.java) { controller(TimelineItems()).markRead(9, null) }

        assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
    }

    @Test
    fun `mark all read hands the render time to the service and redirects home`() {
        val items = TimelineItems()

        val view = controller(items).markAllRead(published.toEpochMilli())

        assertEquals("redirect:/", view)
        assertEquals(published to now, items.markAllCall)
    }
}
