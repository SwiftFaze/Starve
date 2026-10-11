package dev.starve.web

import dev.starve.application.EmbedChecker
import dev.starve.application.PlayerService
import dev.starve.application.TimelineItems
import dev.starve.domain.Playable
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

        val view = controller(mapOf(1L to "https://youtu.be/dQw4w9WgXcQ")).player(1, model)

        assertEquals("player :: content", view)
        assertEquals("youtube", model.getAttribute("kind"))
        assertEquals("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1", model.getAttribute("embedUrl"))
    }

    @Test
    fun `a soundcloud item renders the soundcloud player`() {
        val model = ConcurrentModel()

        controller(mapOf(1L to "https://soundcloud.com/a/b")).player(1, model)

        assertEquals("soundcloud", model.getAttribute("kind"))
        assertEquals(
            "https://w.soundcloud.com/player/?url=https%3A%2F%2Fsoundcloud.com%2Fa%2Fb&auto_play=true",
            model.getAttribute("embedUrl"),
        )
    }

    @Test
    fun `a video with embedding disabled renders the notice with the watch url`() {
        val model = ConcurrentModel()

        controller(mapOf(1L to "https://youtu.be/dQw4w9WgXcQ"), allowed = false).player(1, model)

        assertEquals("disabled", model.getAttribute("kind"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", model.getAttribute("watchUrl"))
        assertNull(model.getAttribute("embedUrl"))
    }

    @Test
    fun `unknown and non-playable items are not found`() {
        val c = controller(mapOf(1L to "https://example.com/x"))

        listOf(1L, 2L).forEach {
            val e = assertThrows(ResponseStatusException::class.java) { c.player(it, ConcurrentModel()) }
            assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
        }
    }
}
