package dev.starve.web

import dev.starve.application.DetailRef
import dev.starve.application.DetailService
import dev.starve.application.GroupService
import dev.starve.application.MemoryGroups
import dev.starve.application.MemoryItems
import dev.starve.application.MemorySources
import dev.starve.application.OWNER
import dev.starve.application.SoundCloudOembed
import dev.starve.application.TimelineItems
import dev.starve.application.TimelineService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.ui.ConcurrentModel
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DetailControllerTest {
    private val published = Instant.parse("2026-10-11T09:30:00Z")
    private val youtube = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    private fun controller(vararg items: DetailRef): DetailController {
        val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        val repository =
            object : MemoryItems() {
                override fun findDetail(
                    owner: Long,
                    id: Long,
                ) = items.firstOrNull { it.id == id }
            }
        return DetailController(
            DetailService(repository, SoundCloudOembed { null }, clock),
            TimelineService(repository, TimelineItems(), MemorySources(), clock),
            GroupService(MemoryGroups(), MemorySources()),
        )
    }

    @Test
    fun `a playable item renders the detail fragment with its view`() {
        val model = ConcurrentModel()
        val thumbnail = "https://img.test/t.jpg"
        val item = DetailRef(7, youtube, "Title", "Channel", published, "Artist", thumbnail, "<p>Hi</p>", null)

        val view = controller(item).detail(OWNER, 7, model)

        assertEquals("detail :: content", view)
        // Thymeleaf reads the view through its getters, so assert through them rather than data-class equality.
        val shown = model.getAttribute("detail") as DetailView
        assertEquals(
            listOf<Any?>(
                7L,
                "STV 0007",
                "Title",
                "Channel",
                "2026-10-11 09:30 UTC",
                "2026-10-11T09:30:00Z",
                "Artist",
                thumbnail,
                "<p>Hi</p>",
                youtube,
            ),
            shown.fields(),
        )
    }

    @Test
    fun `an artist that only repeats the source title is dropped, ignoring case and surrounding space`() {
        val same = DetailRef(7, youtube, "Title", "Submarine", published, " SUBMARINE ", null, null, null)
        val other = DetailRef(8, youtube, "Title", "Submarine", published, "Submarine Music", null, null, null)

        assertEquals(null, artistShown(same))
        assertEquals("Submarine Music", artistShown(other))
    }

    private fun artistShown(item: DetailRef): String? {
        val model = ConcurrentModel()
        controller(item).detail(OWNER, item.id, model)
        return (model.getAttribute("detail") as DetailView).artist
    }

    @Test
    fun `an item that is not playable, or unknown, has no detail view`() {
        val post = DetailRef(7, "https://blog.example/post", "Post", "Blog", published, null, null, null, null)

        assertThrows(ResponseStatusException::class.java) { controller(post).detail(OWNER, 7, ConcurrentModel()) }
            .also { assertEquals(HttpStatus.NOT_FOUND, it.statusCode) }
        assertThrows(ResponseStatusException::class.java) { controller(post).detail(OWNER, 9, ConcurrentModel()) }
            .also { assertEquals(HttpStatus.NOT_FOUND, it.statusCode) }
    }

    private fun DetailView.fields() =
        listOf(
            id,
            catalogueNumber,
            title,
            sourceTitle,
            published,
            publishedIso,
            artist,
            thumbnailUrl,
            description,
            link,
        )

    @Test
    fun `the detail view carries the save toggle for the item`() {
        val model = ConcurrentModel()
        val item = DetailRef(7, youtube, "Title", "Channel", published, null, null, null, null, later = true)

        controller(item).detail(OWNER, 7, model)

        assertEquals(LaterView("detail-later", 7, true, from = "detail"), model.getAttribute("laterToggle"))
    }

    @Test
    fun `the detail view carries the star of the item, filled when it is a favourite`() {
        val model = ConcurrentModel()
        val item = DetailRef(7, youtube, "Title", "Channel", published, null, null, null, null, favourite = true)

        controller(item).detail(OWNER, 7, model)

        assertEquals(StarView("detail-star-7", 7, true, from = "detail"), model.getAttribute("star"))
    }
}
