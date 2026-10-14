package dev.starve.web

import dev.starve.application.MemoryItems
import dev.starve.application.PostRef
import dev.starve.application.ReaderService
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

    private fun controller(vararg posts: PostRef) =
        ReaderController(
            ReaderService(
                object : MemoryItems() {
                    override fun findPost(id: Long) = posts.firstOrNull { it.id == id }
                },
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            ),
        )

    @Test
    fun `a post renders the reader fragment with its view`() {
        val model = ConcurrentModel()
        val post = PostRef(7, "https://blog.example/a", "Title", "Blog", published, "<p>Hi</p>")

        val view = controller(post).reader(7, model)

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

        controller(post).reader(7, model)

        assertEquals(StarView("reader-star", 7, true, fromReader = true), model.getAttribute("star"))
    }

    @Test
    fun `the view exposes each field the template reads`() {
        val model = ConcurrentModel()

        controller(PostRef(7, "https://blog.example/a", "Title", "Blog", published, "<p>Hi</p>")).reader(7, model)

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

        controller(PostRef(7, null, "T", "S", published, null)).reader(7, model)

        val view = model.getAttribute("post") as ReaderView
        assertEquals(null, view.body)
        assertEquals(null, view.link)
    }

    @Test
    fun `a link that is not http or https is not offered as Open original`() {
        val model = ConcurrentModel()

        controller(PostRef(7, "JavaScript:alert(1)", "T", "S", published, "<p>Hi</p>")).reader(7, model)

        assertEquals(null, (model.getAttribute("post") as ReaderView).link)
    }

    @Test
    fun `a playable item is 404`() {
        val reader = controller(PostRef(1, "https://youtu.be/dQw4w9WgXcQ", "T", "S", published, null))

        val failure = assertThrows(ResponseStatusException::class.java) { reader.reader(1, ConcurrentModel()) }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }

    @Test
    fun `an unknown item is 404`() {
        val reader = controller()

        val failure = assertThrows(ResponseStatusException::class.java) { reader.reader(2, ConcurrentModel()) }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }
}
