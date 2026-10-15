package dev.starve.application

import dev.starve.domain.Playable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PlayerServiceTest {
    private val now = Instant.parse("2026-10-11T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val youtube = "https://youtu.be/dQw4w9WgXcQ"

    private class Embeds(
        private val allowed: Boolean,
    ) : EmbedChecker {
        val asked = mutableListOf<Playable.YouTube>()

        override fun allowsEmbedding(video: Playable.YouTube): Boolean {
            asked += video
            return allowed
        }
    }

    private fun service(
        items: TimelineItems,
        embeds: Embeds = Embeds(true),
    ) = PlayerService(items, embeds, clock)

    @Test
    fun `an unknown item is not playable and nothing is marked read`() {
        val items = TimelineItems()

        assertEquals(PlayerResult.NotPlayable, service(items).player(OWNER, 9))
        assertTrue(items.readCalls.isEmpty())
    }

    @Test
    fun `an item without a link or with a non-playable link stays unread`() {
        val items = TimelineItems(links = mapOf(1L to null, 2L to "https://example.com/story"))

        assertEquals(PlayerResult.NotPlayable, service(items).player(OWNER, 1))
        assertEquals(PlayerResult.NotPlayable, service(items).player(OWNER, 2))
        assertTrue(items.readCalls.isEmpty())
    }

    @Test
    fun `an embeddable video is embedded and marked read`() {
        val items = TimelineItems(links = mapOf(1L to youtube))
        val embeds = Embeds(true)

        val result = service(items, embeds).player(OWNER, 1)

        assertEquals(PlayerResult.Embed(Playable.YouTube("dQw4w9WgXcQ"), ref(1, youtube)), result)
        assertEquals(listOf(1L to now), items.readCalls)
        assertEquals(listOf(Playable.YouTube("dQw4w9WgXcQ")), embeds.asked)
    }

    @Test
    fun `a video with embedding disabled falls back to the watch url but is still marked read`() {
        val items = TimelineItems(links = mapOf(1L to youtube))

        val result = service(items, Embeds(false)).player(OWNER, 1)

        val expected = PlayerResult.EmbeddingDisabled("https://www.youtube.com/watch?v=dQw4w9WgXcQ", ref(1, youtube))
        assertEquals(expected, result)
        assertEquals(listOf(1L to now), items.readCalls)
    }

    @Test
    fun `a soundcloud track is embedded without an embedding check`() {
        val track = "https://soundcloud.com/a/b"
        val items = TimelineItems(links = mapOf(1L to track))
        val embeds = Embeds(false)

        val result = service(items, embeds).player(OWNER, 1)

        assertEquals(PlayerResult.Embed(Playable.SoundCloud(track), ref(1, track)), result)
        assertTrue(embeds.asked.isEmpty())
        assertEquals(listOf(1L to now), items.readCalls)
    }

    @Test
    fun `the item is looked up and marked read for the user asked for`() {
        val items = TimelineItems(links = mapOf(1L to youtube))

        service(items).player(OTHER_OWNER, 1)

        assertEquals(listOf(OTHER_OWNER, OTHER_OWNER), items.calledFor)
    }
}
