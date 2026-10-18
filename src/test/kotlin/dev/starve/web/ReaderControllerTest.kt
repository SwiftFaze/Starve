package dev.starve.web

import dev.starve.application.ArticleFetcher
import dev.starve.application.GroupService
import dev.starve.application.HtmlText
import dev.starve.application.ListFilter
import dev.starve.application.ListPosition
import dev.starve.application.MemoryArticles
import dev.starve.application.MemoryGroups
import dev.starve.application.MemoryItems
import dev.starve.application.MemorySources
import dev.starve.application.Neighbours
import dev.starve.application.OWNER
import dev.starve.application.PostRef
import dev.starve.application.ReaderService
import dev.starve.application.TimelineItems
import dev.starve.application.TimelineService
import dev.starve.domain.FeedOnlyHosts
import dev.starve.domain.TimelineItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.ui.ConcurrentModel
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReaderControllerTest {
    private val published = Instant.parse("2026-10-11T09:30:00Z")

    private val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

    private fun controller(
        vararg posts: PostRef,
        canned: Neighbours = Neighbours(null, null),
    ): ReaderController {
        val items =
            object : MemoryItems() {
                override fun findPost(
                    owner: Long,
                    id: Long,
                ) = posts.firstOrNull { it.id == id }

                override fun neighbours(
                    owner: Long,
                    position: ListPosition,
                    filter: ListFilter,
                ) = canned
            }
        return ReaderController(
            ReaderService(items, MemoryArticles(), clock, ArticleFetcher { null }, FeedOnlyHosts(emptyList()), HtmlText { it }),
            TimelineService(items, TimelineItems(), MemorySources(), clock),
            GroupService(MemoryGroups(), MemorySources()),
        )
    }

    @Test
    fun `the reader links Previous and Next into the list it was opened from`() {
        val model = ConcurrentModel()
        val post = PostRef(7, "https://blog.example/a", "Title", "Blog", published, "<p>Hi</p>")
        val newer = TimelineItem(3, "Blog", "Newer", published, null, false, playable = true)
        val older = TimelineItem(4, "Blog", "Older", published, null, false)

        controller(post, canned = Neighbours(newer, older)).reader(OWNER, 7, model, TimelineQuery(show = "all"))

        val view = model.getAttribute("post") as ReaderView
        assertEquals(PostLink("/items/3/detail?show=all"), view.previous)
        assertEquals(PostLink("/items/4/reader?show=all"), view.next)
    }

    @Test
    fun `a post renders the reader fragment with its view`() {
        val model = ConcurrentModel()
        val post = PostRef(7, "https://blog.example/a", "Title", "Blog", published, "<p>Hi</p>")

        val view = controller(post).reader(OWNER, 7, model)

        assertEquals("reader :: content", view)
        assertEquals(
            ReaderView(
                "STV 0007",
                "Title",
                "Blog",
                "2026-10-11 09:30 UTC",
                "2026-10-11T09:30:00Z",
                "<p>Hi</p>",
                "https://blog.example/a",
                null,
                null,
            ),
            model.getAttribute("post"),
        )
    }

    @Test
    fun `the reader carries the star for the post's favourite state`() {
        val model = ConcurrentModel()
        val post = PostRef(7, "https://blog.example/a", "Title", "Blog", published, null, favourite = true)

        controller(post).reader(OWNER, 7, model)

        assertEquals(StarView("reader-star-7", 7, true, from = "reader"), model.getAttribute("star"))
    }

    @Test
    fun `the view exposes each field the template reads`() {
        val model = ConcurrentModel()

        val post = PostRef(7, "https://blog.example/a", "Title", "Blog", published, "<p>Hi</p>")
        controller(post).reader(OWNER, 7, model)

        val view = model.getAttribute("post") as ReaderView
        assertEquals("STV 0007", view.catalogueNumber)
        assertEquals("Title", view.title)
        assertEquals("Blog", view.sourceTitle)
        assertEquals("2026-10-11 09:30 UTC", view.published)
        assertEquals("2026-10-11T09:30:00Z", view.publishedIso)
    }

    @Test
    fun `a post without a body or a link has neither in its view`() {
        val model = ConcurrentModel()

        controller(PostRef(7, null, "T", "S", published, null)).reader(OWNER, 7, model)

        val view = model.getAttribute("post") as ReaderView
        assertEquals(null, view.body)
        assertEquals(null, view.link)
    }

    @Test
    fun `a link that is not http or https is not offered as Open original`() {
        val model = ConcurrentModel()

        controller(PostRef(7, "JavaScript:alert(1)", "T", "S", published, "<p>Hi</p>")).reader(OWNER, 7, model)

        assertEquals(null, (model.getAttribute("post") as ReaderView).link)
    }

    @Test
    fun `a playable item is 404`() {
        val reader = controller(PostRef(1, "https://youtu.be/dQw4w9WgXcQ", "T", "S", published, null))

        val failure = assertThrows(ResponseStatusException::class.java) { reader.reader(OWNER, 1, ConcurrentModel()) }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }

    @Test
    fun `an unknown item is 404`() {
        val reader = controller()

        val failure = assertThrows(ResponseStatusException::class.java) { reader.reader(OWNER, 2, ConcurrentModel()) }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }

    @Test
    fun `the reader carries the save toggle for the post`() {
        val model = ConcurrentModel()
        val post = PostRef(7, "https://blog.example/a", "Title", "Blog", published, "<p>Hi</p>", later = true)

        controller(post).reader(OWNER, 7, model)

        assertEquals(LaterView("reader-later", 7, true, from = "reader"), model.getAttribute("laterToggle"))
    }
}
