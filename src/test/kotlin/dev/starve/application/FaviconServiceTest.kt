package dev.starve.application

import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class FaviconServiceTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")

    private class Finder(
        private val result: () -> String?,
    ) : FaviconFinder {
        val asked = mutableListOf<String>()

        override fun find(siteUrl: String): String? {
            asked += siteUrl
            return result()
        }
    }

    private fun source(
        checkedAt: Instant? = null,
        icon: String? = null,
    ) = Source(3, "https://feeds.example/a.xml", "Blog", Validators(), null, null, null, icon, checkedAt)

    @Test
    fun `a due source gets the icon the finder found, from the site link`() {
        val sources = MemorySources()
        val finder = Finder { "https://cdn.example/i.png" }

        FaviconService(sources, finder).refresh(source(), "https://blog.example/", now)

        assertEquals(listOf("https://blog.example/"), finder.asked)
        assertEquals(listOf(Triple(3L, "https://cdn.example/i.png", now)), sources.favicons)
    }

    @Test
    fun `a source checked recently is not looked up again`() {
        val sources = MemorySources()
        val finder = Finder { "https://cdn.example/i.png" }

        FaviconService(sources, finder).refresh(source(checkedAt = now.minusSeconds(3600)), null, now)

        assertTrue(finder.asked.isEmpty())
        assertTrue(sources.favicons.isEmpty())
    }

    @Test
    fun `an absent icon is recorded as none, and the check time is stamped`() {
        val sources = MemorySources()

        FaviconService(sources, Finder { null }).refresh(source(icon = "https://old.example/i.png"), null, now)

        assertEquals(listOf(Triple(3L, null, now)), sources.favicons)
    }

    @Test
    fun `a lookup that cannot reach the site keeps the icon it had`() {
        val sources = MemorySources()
        val finder = Finder { throw FeedFetchException("timed out") }

        FaviconService(sources, finder).refresh(source(icon = "https://kept.example/i.png"), null, now)

        assertEquals(listOf(Triple(3L, "https://kept.example/i.png", now)), sources.favicons)
    }
}
