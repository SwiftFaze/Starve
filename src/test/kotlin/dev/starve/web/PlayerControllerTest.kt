package dev.starve.web

import dev.starve.application.EmbedChecker
import dev.starve.application.ItemRef
import dev.starve.application.MemoryItems
import dev.starve.application.OWNER
import dev.starve.application.PlayerService
import dev.starve.application.TimelineItems
import dev.starve.domain.Playable
import dev.starve.domain.Waveform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.ui.ConcurrentModel
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PlayerControllerTest {
    private fun controller(
        links: Map<Long, String?>,
        allowed: Boolean = true,
    ) = PlayerController(
        PlayerService(
            TimelineItems(links = links),
            object : EmbedChecker {
                override fun allowsEmbedding(video: Playable.YouTube) = allowed
            },
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        ),
    )

    @Test
    fun `a youtube item renders the youtube player`() {
        val model = ConcurrentModel()

        val view = controller(mapOf(1L to "https://youtu.be/dQw4w9WgXcQ")).player(OWNER, 1, model)

        assertEquals("player :: content", view)
        assertEquals("youtube", model.getAttribute("kind"))
        assertEquals(
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ" +
                "?autoplay=1&enablejsapi=1&controls=1&rel=0&iv_load_policy=3&playsinline=1",
            model.getAttribute("embedUrl"),
        )
    }

    @Test
    fun `the player names the playing item with its catalogue number`() {
        val model = ConcurrentModel()

        controller(mapOf(7L to "https://soundcloud.com/a/b")).player(OWNER, 7, model)

        assertEquals(NowPlayingView("STV 0007", "Item 7", "Source", null), model.getAttribute("item"))
    }

    @Test
    fun `the player carries a waveform seeded by the item id`() {
        val model = ConcurrentModel()

        controller(mapOf(7L to "https://soundcloud.com/a/b")).player(OWNER, 7, model)

        assertEquals(Waveform.bars(7), model.getAttribute("waveform"))
    }

    @Test
    fun `a soundcloud item renders the soundcloud player`() {
        val model = ConcurrentModel()

        controller(mapOf(1L to "https://soundcloud.com/a/b")).player(OWNER, 1, model)

        assertEquals("soundcloud", model.getAttribute("kind"))
        assertEquals(
            "https://w.soundcloud.com/player/?url=https%3A%2F%2Fsoundcloud.com%2Fa%2Fb&auto_play=true",
            model.getAttribute("embedUrl"),
        )
    }

    @Test
    fun `a video with embedding disabled renders the notice with the watch url`() {
        val model = ConcurrentModel()

        controller(mapOf(1L to "https://youtu.be/dQw4w9WgXcQ"), allowed = false).player(OWNER, 1, model)

        assertEquals("disabled", model.getAttribute("kind"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", model.getAttribute("watchUrl"))
        assertNull(model.getAttribute("embedUrl"))
    }

    @Test
    fun `unknown and non-playable items are not found`() {
        val c = controller(mapOf(1L to "https://example.com/x"))

        listOf(1L, 2L).forEach {
            val e = assertThrows(ResponseStatusException::class.java) { c.player(OWNER, it, ConcurrentModel()) }
            assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
        }
    }

    @Test
    fun `the deck carries the star of the playing item, empty when it is not a favourite`() {
        val model = ConcurrentModel()

        controller(mapOf(1L to "https://youtu.be/dQw4w9WgXcQ")).player(OWNER, 1, model)

        assertEquals(StarView("deck-star-1", 1, false, from = "deck"), model.getAttribute("star"))
    }

    @Test
    fun `the deck carries a filled star for a favourite`() {
        val favourite =
            object : MemoryItems() {
                override fun find(
                    owner: Long,
                    id: Long,
                ) = ItemRef(id, "https://soundcloud.com/a/b", "Item $id", "Source", null, favourite = true)
            }
        val model = ConcurrentModel()
        PlayerController(
            PlayerService(
                favourite,
                object : EmbedChecker {
                    override fun allowsEmbedding(video: Playable.YouTube) = true
                },
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            ),
        ).player(OWNER, 3, model)

        assertEquals(StarView("deck-star-3", 3, true, from = "deck"), model.getAttribute("star"))
    }

    @Test
    fun `a video whose embedding is disabled still carries its deck star`() {
        val model = ConcurrentModel()

        controller(mapOf(1L to "https://youtu.be/dQw4w9WgXcQ"), allowed = false).player(OWNER, 1, model)

        assertEquals("disabled", model.getAttribute("kind"))
        assertEquals(StarView("deck-star-1", 1, false, from = "deck"), model.getAttribute("star"))
    }
}
