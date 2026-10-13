package dev.starve.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReaderServiceTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")

    private class Posts(
        private val posts: Map<Long, PostRef>,
    ) : MemoryItems() {
        val read = mutableListOf<Pair<Long, Instant>>()

        override fun findPost(id: Long): PostRef? = posts[id]

        override fun markRead(
            id: Long,
            at: Instant,
        ): Boolean {
            read += id to at
            return true
        }
    }

    private fun post(
        id: Long,
        link: String?,
        body: String? = "<p>Hi</p>",
    ) = PostRef(id, link, "Post $id", "Blog", Instant.EPOCH, body)

    private fun service(vararg posts: PostRef): Pair<ReaderService, Posts> {
        val items = Posts(posts.associateBy { it.id })
        return ReaderService(items, Clock.fixed(now, ZoneOffset.UTC)) to items
    }

    @Test
    fun `a blog post is returned and marked read`() {
        val (service, items) = service(post(1, "https://blog.example/a"))

        assertEquals(post(1, "https://blog.example/a"), service.open(1))
        assertEquals(listOf(1L to now), items.read)
    }

    @Test
    fun `a post without a body or a link is still opened and marked read`() {
        val (service, items) = service(post(1, null, body = null))

        assertEquals(post(1, null, body = null), service.open(1))
        assertEquals(listOf(1L to now), items.read)
    }

    @Test
    fun `an unknown item is not readable and nothing is marked read`() {
        val (service, items) = service()

        assertNull(service.open(9))
        assertTrue(items.read.isEmpty())
    }

    @Test
    fun `a youtube item is not readable and stays unread`() {
        val (service, items) = service(post(1, "https://youtu.be/dQw4w9WgXcQ"))

        assertNull(service.open(1))
        assertTrue(items.read.isEmpty())
    }

    @Test
    fun `a soundcloud item is not readable and stays unread`() {
        val (service, items) = service(post(2, "https://soundcloud.com/a/b"))

        assertNull(service.open(2))
        assertTrue(items.read.isEmpty())
    }
}
