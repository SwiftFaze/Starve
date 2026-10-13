package dev.starve.web

import dev.starve.application.GroupService
import dev.starve.application.MemoryGroups
import dev.starve.application.MemorySources
import dev.starve.application.TimelineItems
import dev.starve.application.TimelineService
import dev.starve.domain.Cursor
import dev.starve.domain.Group
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
    private val music = Group(1, "music")
    private val news = Group(2, "News")
    private val source = Source(1, "http://a.test/f", "A", Validators(), null, null)

    private fun controller(items: TimelineItems) =
        HomeController(
            TimelineService(items, items, MemorySources(mutableListOf(source)), Clock.fixed(now, ZoneOffset.UTC)),
            GroupService(MemoryGroups(mutableListOf(music, news), mapOf(1L to 3, 2L to 0), 5), MemorySources()),
        )

    private fun row(id: Long) = TimelineItem(id, "A", "T$id", published, "http://img.test/$id", id == 1L)

    private fun rows(count: Int) = (1..count).map { row(it.toLong()) }

    @Test
    fun `home renders formatted views and the render timestamp`() {
        val model = ConcurrentModel()

        val view = controller(TimelineItems(listOf(row(1)))).home(TimelineQuery(), null, model)

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

        controller(TimelineItems(listOf(row(2)))).home(TimelineQuery(), null, model)

        val item = (model.getAttribute("items") as List<*>).single() as TimelineItemView
        assertFalse(item.read)
    }

    @Test
    fun `show all is passed through and keeps the cursor link`() {
        val items = TimelineItems((1L..TimelineRules.PAGE_SIZE + 1).map { row(it) })
        val model = ConcurrentModel()

        controller(items).home(TimelineQuery(show = "all"), null, model)

        assertEquals(true, items.requested!!.second)
        assertEquals("/?after=${published.toEpochMilli()}_50&show=all", model.getAttribute("moreHref"))
    }

    @Test
    fun `an htmx request with a cursor returns only the fragment`() {
        val view = controller(TimelineItems()).home(TimelineQuery(after = "1_2"), "true", ConcurrentModel())

        assertEquals("home :: more", view)
    }

    @Test
    fun `an htmx request without a cursor returns the full page`() {
        assertEquals("home", controller(TimelineItems()).home(TimelineQuery(), "true", ConcurrentModel()))
    }

    @Test
    fun `the favourites view lists favourites and ignores the show and group parameters`() {
        val items = TimelineItems(favouriteRows = listOf(row(1)))
        val model = ConcurrentModel()

        controller(items).home(TimelineQuery(show = "all", group = "99", favourites = "1"), null, model)

        assertEquals(null to TimelineRules.PAGE_SIZE + 1, items.favouritesRequested)
        assertNull(items.requested)
        assertEquals(true, model.getAttribute("favourites"))
        assertEquals(false, model.getAttribute("showAll"))
        assertEquals(false, model.getAttribute("emptyFavourites"))
    }

    @Test
    fun `an empty favourites view is flagged so the page can say so`() {
        val model = ConcurrentModel()

        controller(TimelineItems()).home(TimelineQuery(favourites = "1"), null, model)

        assertEquals(true, model.getAttribute("emptyFavourites"))
    }

    @Test
    fun `the favourites view keeps the filter in its load-more link and has no unread count`() {
        val items = TimelineItems(favouriteRows = (1L..TimelineRules.PAGE_SIZE + 1).map { row(it) })
        val model = ConcurrentModel()

        controller(items).home(TimelineQuery(favourites = "1"), null, model)

        assertEquals("/?after=${published.toEpochMilli()}_50&favourites=1", model.getAttribute("moreHref"))
        assertEquals(SeriesView("Favourites", null), model.getAttribute("series"))
    }

    @Test
    fun `the Favourites entry is the one current entry in the favourites view`() {
        val model = ConcurrentModel()

        controller(TimelineItems()).home(TimelineQuery(favourites = "1"), null, model)

        assertEquals(NavEntryView("Favourites", "/?favourites=1", true), model.getAttribute("favouritesEntry"))
        val groups = model.getAttribute("nav") as List<*>
        assertEquals(listOf(false, false, false), groups.map { (it as NavEntryView).current })
    }

    @Test
    fun `the Favourites entry is not current outside the favourites view`() {
        val model = ConcurrentModel()

        controller(TimelineItems()).home(TimelineQuery(), null, model)

        assertEquals(NavEntryView("Favourites", "/?favourites=1", false), model.getAttribute("favouritesEntry"))
        val groups = model.getAttribute("nav") as List<*>
        assertEquals(true, (groups.first() as NavEntryView).current)
    }

    @Test
    fun `a row presents its star for the item's favourite state`() {
        val model = ConcurrentModel()

        controller(TimelineItems(listOf(row(1).copy(favourite = true), row(2)))).home(TimelineQuery(), null, model)

        val stars = (model.getAttribute("items") as List<*>).map { (it as TimelineItemView).star }
        assertEquals(listOf(StarView("star-1", 1, true), StarView("star-2", 2, false)), stars)
    }

    @Test
    fun `a playable row is presented as playable`() {
        val model = ConcurrentModel()
        val playable = row(3).copy(playable = true)

        controller(TimelineItems(listOf(playable, row(4)))).home(TimelineQuery(), null, model)

        val views = (model.getAttribute("items") as List<*>).map { (it as TimelineItemView).playable }
        assertEquals(listOf(true, false), views)
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

        val view = controller(items).markAllRead(published.toEpochMilli(), null)

        assertEquals("redirect:/", view)
        assertEquals(published to now, items.markAllCall)
    }

    @Test
    fun `a group filter reaches the service and is kept by the cursor link and the toggle`() {
        val items = TimelineItems((1L..TimelineRules.PAGE_SIZE + 1).map { row(it) })
        val model = ConcurrentModel()

        controller(items).home(TimelineQuery(show = "all", group = "2"), null, model)

        assertEquals(2L, items.requestedGroup)
        assertEquals("/?after=${published.toEpochMilli()}_50&show=all&group=2", model.getAttribute("moreHref"))
        assertEquals("/?group=2", model.getAttribute("toggleHref"))
        assertEquals(2L, model.getAttribute("groupId"))
    }

    @Test
    fun `the series is the current group with its unread count, or All with the total`() {
        val group = ConcurrentModel()
        val all = ConcurrentModel()

        controller(TimelineItems()).home(TimelineQuery(group = "1"), null, group)
        controller(TimelineItems()).home(TimelineQuery(), null, all)

        assertEquals(SeriesView("music", 3), group.getAttribute("series"))
        assertEquals(SeriesView("All", 5), all.getAttribute("series"))
    }

    @Test
    fun `the navigation lists All then groups alphabetically with unread counts and marks the current one`() {
        val model = ConcurrentModel()

        controller(TimelineItems()).home(TimelineQuery(group = "1"), null, model)

        val expected =
            listOf(
                NavEntryView("All (5)", "/", false),
                NavEntryView("music (3)", "/?group=1", true),
                NavEntryView("News (0)", "/?group=2", false),
            )
        assertEquals(expected, model.getAttribute("nav"))

        val all = ConcurrentModel()
        controller(TimelineItems()).home(TimelineQuery(), null, all)
        assertEquals("/?show=all", all.getAttribute("toggleHref"))
    }

    @Test
    fun `unknown or non-numeric groups are not found`() {
        listOf("99", "abc", "").forEach { raw ->
            val e =
                assertThrows(ResponseStatusException::class.java) {
                    controller(TimelineItems()).home(TimelineQuery(group = raw), null, ConcurrentModel())
                }

            assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
        }
    }

    @Test
    fun `mark all read inside a group is scoped and redirects back to the group`() {
        val items = TimelineItems()

        val view = controller(items).markAllRead(published.toEpochMilli(), 2)

        assertEquals("redirect:/?group=2", view)
        assertEquals(2L, items.markAllGroup)
    }

    @Test
    fun `a search shows the matches and keeps the text in the box`() {
        val items = TimelineItems(listOf(row(1)))
        val model = ConcurrentModel()

        val view = controller(items).home(TimelineQuery(q = "match"), null, model)

        assertEquals("home", view)
        assertEquals("match", model.getAttribute("query"))
        assertEquals(true, model.getAttribute("searching"))
        assertEquals(1, (model.getAttribute("items") as List<*>).size)
        assertEquals(Triple("\"match\"*", null, TimelineRules.PAGE_SIZE + 1), items.searched)
    }

    @Test
    fun `a search ignores the group and show settings`() {
        val items = TimelineItems(listOf(row(1)))

        controller(items).home(TimelineQuery(q = "match", show = "all", group = "99"), null, ConcurrentModel())

        assertNull(items.requestedGroup)
        assertNull(items.requested)
    }

    @Test
    fun `a blank search is the normal timeline`() {
        val items = TimelineItems(listOf(row(1)))
        val model = ConcurrentModel()

        controller(items).home(TimelineQuery(q = "   "), null, model)

        assertEquals(false, model.getAttribute("searching"))
        assertEquals(Triple<Cursor?, Boolean, Int>(null, false, TimelineRules.PAGE_SIZE + 1), items.requested)
    }

    @Test
    fun `a search with more pages links to the next page with the search kept`() {
        val model = ConcurrentModel()

        controller(TimelineItems(rows(TimelineRules.PAGE_SIZE + 1)))
            .home(TimelineQuery(q = "a b"), null, model)

        val href = model.getAttribute("moreHref") as String
        assertTrue(href.startsWith("/?q=a+b&after="), href)
    }

    @Test
    fun `an htmx search returns only the sheet fragment`() {
        val view = controller(TimelineItems(listOf(row(1)))).home(TimelineQuery(q = "match"), "true", ConcurrentModel())

        assertEquals("home :: sheet", view)
    }

    @Test
    fun `an htmx request for the timeline without a query still returns the full page`() {
        val view = controller(TimelineItems(listOf(row(1)))).home(TimelineQuery(), "true", ConcurrentModel())

        assertEquals("home", view)
    }
}
