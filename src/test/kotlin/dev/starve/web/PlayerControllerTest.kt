package dev.starve.web

import dev.starve.application.EmbedChecker
import dev.starve.application.ItemRef
import dev.starve.application.MemoryItems
import dev.starve.application.OWNER
import dev.starve.application.PlayerService
import dev.starve.application.TimelineItems
import dev.starve.domain.AudioEnclosure
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
        audio: Map<Long, AudioEnclosure> = emptyMap(),
    ) = PlayerController(
        PlayerService(
            TimelineItems(links = links, audio = audio),
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
    fun `an episode renders the audio player with its length and the page to listen on`() {
        val model = ConcurrentModel()
        val episode = AudioEnclosure("https://cdn.example/ep.mp3", 3723)

        controller(mapOf(1L to "https://show.example/ep"), audio = mapOf(1L to episode)).player(OWNER, 1, model)

        assertEquals("audio", model.getAttribute("kind"))
        assertEquals("https://cdn.example/ep.mp3", model.getAttribute("embedUrl"))
        assertEquals("https://show.example/ep", model.getAttribute("listenUrl"))
        assertEquals(3723L, model.getAttribute("duration"))
        assertEquals("1:02:03", model.getAttribute("total"))
    }

    @Test
    fun `an episode without a link or a length offers its file and no total`() {
        val model = ConcurrentModel()
        val episode = AudioEnclosure("https://cdn.example/ep.mp3")

        controller(mapOf(1L to null), audio = mapOf(1L to episode)).player(OWNER, 1, model)

        assertEquals("https://cdn.example/ep.mp3", model.getAttribute("listenUrl"))
        assertNull(model.getAttribute("duration"))
        assertNull(model.getAttribute("total"))
    }

    @Test
    fun `play times show minutes and seconds, and hours once there are any`() {
        assertEquals("0:00", playTime(0))
        assertEquals("0:59", playTime(59))
        assertEquals("41:12", playTime(2472))
        assertEquals("59:59", playTime(3599))
        assertEquals("1:00:00", playTime(3600))
        assertEquals("10:02:03", playTime(36123))
    }

    @Test
    fun `the player names the playing item with its catalogue number`() {
        val model = ConcurrentModel()

        controller(mapOf(7L to "https://soundcloud.com/a/b")).player(OWNER, 7, model)

        assertEquals(NowPlayingView(7, "STV 0007", "Item 7", "Source", null), model.getAttribute("item"))
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
