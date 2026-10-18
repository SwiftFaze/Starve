package dev.starve.application

import dev.starve.domain.ReadMark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DetailServiceTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val published = Instant.parse("2026-10-12T08:00:00Z")
    private val track = "https://soundcloud.com/artist/track"
    private val feedThumbnail = "https://img.example/feed.jpg"
    private val youtube = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    private class Details(
        private val items: Map<Long, DetailRef>,
    ) : MemoryItems() {
        val read = mutableListOf<Pair<Long, ReadMark>>()
        val saved = mutableMapOf<Long, Oembed>()

        override fun findDetail(
            owner: Long,
            id: Long,
        ): DetailRef? = items[id]

        override fun markRead(
            owner: Long,
            id: Long,
            mark: ReadMark,
        ): Boolean {
            read += id to mark
            return true
        }

        override fun saveOembed(
            id: Long,
            oembed: Oembed,
        ) {
            saved[id] = oembed
        }
    }

    private class Oembeds(
        private val answer: Oembed?,
    ) : SoundCloudOembed {
        val asked = mutableListOf<String>()

        override fun fetch(trackUrl: String): Oembed? {
            asked += trackUrl
            return answer
        }
    }

    private fun item(
        id: Long,
        link: String?,
        oembed: Oembed? = null,
    ) = DetailRef(id, link, "Title $id", "Source", published, "Feed Artist", feedThumbnail, "<p>Feed</p>", oembed)

    private fun service(
        vararg items: DetailRef,
        answer: Oembed? = null,
    ): Triple<DetailService, Details, Oembeds> {
        val repository = Details(items.associateBy { it.id })
        val oembeds = Oembeds(answer)
        return Triple(DetailService(repository, oembeds, Clock.fixed(now, ZoneOffset.UTC)), repository, oembeds)
    }

    /** What the detail view shows for item [id] with nothing laid over the feed data. */
    private fun feedDetail(
        id: Long,
        link: String?,
    ) = ItemDetail(id, link, "Title $id", "Source", published, "Feed Artist", feedThumbnail, "<p>Feed</p>")

    @Test
    fun `the detail reports whether the item is a favourite`() {
        val (service, _, _) = service(item(1, youtube).copy(favourite = true), item(2, youtube))

        assertTrue(service.open(OWNER, 1)!!.favourite)
        assertFalse(service.open(OWNER, 2)!!.favourite)
    }

    @Test
    fun `the detail reports whether the item is saved for later`() {
        val (service, _, _) = service(item(1, youtube).copy(later = true), item(2, youtube))

        assertTrue(service.open(OWNER, 1)!!.later)
        assertFalse(service.open(OWNER, 2)!!.later)
    }

    @Test
    fun `a youtube video shows its feed details and is marked read without an oembed request`() {
        val (service, items, oembeds) = service(item(1, youtube))

        assertEquals(feedDetail(1, youtube), service.open(OWNER, 1))
        assertEquals(listOf(1L to ReadMark.opened(now)), items.read)
        assertTrue(oembeds.asked.isEmpty())
    }

    @Test
    fun `a soundcloud track is laid over with its oembed details, which are stored`() {
        val oembed = Oembed("Oembed Artist", "https://i1.example/art.jpg", "<p>Oembed</p>")
        val (service, items, oembeds) = service(item(1, track), answer = oembed)

        val laidOver = feedDetail(1, track).copy(artist = "Oembed Artist", thumbnailUrl = "https://i1.example/art.jpg")
        assertEquals(laidOver.copy(description = "<p>Oembed</p>"), service.open(OWNER, 1))
        assertEquals(listOf(track), oembeds.asked)
        assertEquals(mapOf(1L to oembed), items.saved)
    }

    @Test
    fun `a cached oembed response is used without asking SoundCloud again`() {
        val cached = Oembed("Cached Artist", null, null)
        val (service, _, oembeds) = service(item(1, track, oembed = cached))

        assertEquals("Cached Artist", service.open(OWNER, 1)?.artist)
        assertTrue(oembeds.asked.isEmpty())
    }

    @Test
    fun `a failed oembed fetch shows the feed data and is not stored`() {
        val (service, items, _) = service(item(1, track), answer = null)

        assertEquals(feedDetail(1, track), service.open(OWNER, 1))
        assertTrue(items.saved.isEmpty())
        assertEquals(listOf(1L to ReadMark.opened(now)), items.read)
    }

    @Test
    fun `an oembed without a description or author keeps the feed's description and author`() {
        val (service, _, _) = service(item(1, track), answer = Oembed(null, "https://i1.example/art.jpg", null))

        assertEquals(feedDetail(1, track).copy(thumbnailUrl = "https://i1.example/art.jpg"), service.open(OWNER, 1))
    }

    @Test
    fun `a non-playable item has no detail view and nothing is marked read`() {
        val (service, items, _) = service(item(1, "https://blog.example/post"))

        assertNull(service.open(OWNER, 1))
        assertTrue(items.read.isEmpty())
    }

    @Test
    fun `an unknown item has no detail view`() {
        val (service, items, _) = service()

        assertNull(service.open(OWNER, 9))
        assertTrue(items.read.isEmpty())
    }
}
