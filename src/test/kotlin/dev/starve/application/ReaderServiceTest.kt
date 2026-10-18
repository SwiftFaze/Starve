package dev.starve.application

import dev.starve.domain.ArticleRules
import dev.starve.domain.FeedOnlyHosts
import dev.starve.domain.ReadMark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ReaderServiceTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")

    private class Posts(
        private val posts: Map<Long, PostRef>,
    ) : MemoryItems() {
        val read = mutableListOf<Pair<Long, ReadMark>>()
        val articles = MemoryArticles()

        override fun findPost(
            owner: Long,
            id: Long,
        ): PostRef? = posts[id]

        override fun markRead(
            owner: Long,
            id: Long,
            mark: ReadMark,
        ): Boolean {
            read += id to mark
            return true
        }
    }

    /** Answers every fetch with [page] and records the urls asked for. */
    private class Pages(
        private val page: FetchedArticle?,
    ) : ArticleFetcher {
        val requested = mutableListOf<String>()

        override fun fetch(url: String): FetchedArticle? {
            requested += url
            return page
        }
    }

    private val feedOnly = FeedOnlyHosts(listOf("github.com"))

    private val tags = HtmlText { it.replace(Regex("<[^>]*>"), "") }

    private fun post(
        id: Long,
        link: String?,
        body: String? = "<p>Hi</p>",
    ) = PostRef(id, link, "Post $id", "Blog", Instant.EPOCH, body)

    private fun article(length: Int) = "<p>" + "x".repeat(length) + "</p>"

    private fun service(
        page: FetchedArticle? = null,
        vararg posts: PostRef,
    ): Triple<ReaderService, Posts, Pages> {
        val items = Posts(posts.associateBy { it.id })
        val pages = Pages(page)
        return Triple(ReaderService(items, items.articles, Clock.fixed(now, ZoneOffset.UTC), pages, feedOnly, tags), items, pages)
    }

    @Test
    fun `a blog post is returned and marked read`() {
        val (service, items, _) = service(null, post(1, "https://blog.example/a", body = article(800)))

        assertEquals(post(1, "https://blog.example/a", body = article(800)), service.open(OWNER, 1))
        assertEquals(listOf(1L to ReadMark.opened(now)), items.read)
    }

    @Test
    fun `a post without a body or a link is still opened and marked read`() {
        val (service, items, pages) = service(null, post(1, null, body = null))

        assertEquals(post(1, null, body = null), service.open(OWNER, 1))
        assertEquals(listOf(1L to ReadMark.opened(now)), items.read)
        assertTrue(pages.requested.isEmpty())
    }

    @Test
    fun `an unknown item is not readable and nothing is marked read`() {
        val (service, items, _) = service()

        assertNull(service.open(OWNER, 9))
        assertTrue(items.read.isEmpty())
    }

    @Test
    fun `a youtube item is not readable and stays unread`() {
        val (service, items, pages) = service(null, post(1, "https://youtu.be/dQw4w9WgXcQ"))

        assertNull(service.open(OWNER, 1))
        assertTrue(items.read.isEmpty())
        assertTrue(pages.requested.isEmpty())
    }

    @Test
    fun `a soundcloud item is not readable and stays unread`() {
        val (service, items, _) = service(null, post(2, "https://soundcloud.com/a/b"))

        assertNull(service.open(OWNER, 2))
        assertTrue(items.read.isEmpty())
    }

    @Test
    fun `a teaser is replaced by a longer extracted article and the check is stored`() {
        val teaser = post(1, "https://blog.example/a", body = "<p>Teaser</p>")
        val (service, items, pages) = service(FetchedArticle(article(800), null), teaser)

        val shown = service.open(OWNER, 1)

        assertEquals(article(800), shown?.body)
        assertEquals(listOf("https://blog.example/a"), pages.requested)
        assertEquals(listOf(ArticleCheck(1, article(800), null, now)), items.articles.articleChecks)
    }

    @Test
    fun `an extraction no longer than the teaser keeps the teaser and records the check`() {
        val teaser = post(1, "https://blog.example/a", body = article(300))
        val (service, items, _) = service(FetchedArticle(article(100), null), teaser)

        assertEquals(article(300), service.open(OWNER, 1)?.body)
        assertEquals(listOf(ArticleCheck(1, null, null, now)), items.articles.articleChecks)
    }

    @Test
    fun `an extraction exactly as long as the teaser keeps the teaser`() {
        val teaser = post(1, "https://blog.example/a", body = article(300))
        val (service, items, _) = service(FetchedArticle(article(300).replace("x", "y"), null), teaser)

        assertEquals(article(300), service.open(OWNER, 1)?.body)
        assertEquals(listOf(ArticleCheck(1, null, null, now)), items.articles.articleChecks)
    }

    @Test
    fun `a failed fetch keeps the teaser, records the check and still marks the item read`() {
        val (service, items, _) = service(null, post(1, "https://blog.example/a", body = "<p>Teaser</p>"))

        assertEquals("<p>Teaser</p>", service.open(OWNER, 1)?.body)
        assertEquals(listOf(ArticleCheck(1, null, null, now)), items.articles.articleChecks)
        assertEquals(listOf(1L to ReadMark.opened(now)), items.read)
    }

    @Test
    fun `a full-length body is never fetched`() {
        val full = post(1, "https://blog.example/a", body = article(800))
        val (service, _, pages) = service(FetchedArticle(article(900), null), full)

        service.open(OWNER, 1)

        assertTrue(pages.requested.isEmpty())
    }

    @Test
    fun `a page checked less than a day ago is not fetched again`() {
        val checked =
            post(1, "https://blog.example/a", body = null)
                .copy(articleCheckedAt = now.minus(Duration.ofHours(23)))
        val (service, _, pages) = service(FetchedArticle(article(800), null), checked)

        assertNull(service.open(OWNER, 1)?.body)
        assertTrue(pages.requested.isEmpty())
    }

    @Test
    fun `a page checked over a day ago is fetched again`() {
        val stale =
            post(1, "https://blog.example/a", body = null)
                .copy(articleCheckedAt = now.minus(ArticleRules.RECHECK_AFTER).minusSeconds(1))
        val (service, _, pages) = service(FetchedArticle(article(800), null), stale)

        assertEquals(article(800), service.open(OWNER, 1)?.body)
        assertEquals(listOf("https://blog.example/a"), pages.requested)
    }

    @Test
    fun `a non-http link is never fetched`() {
        val (service, _, pages) = service(FetchedArticle(article(800), null), post(1, "ftp://blog.example/a", null))

        assertNull(service.open(OWNER, 1)?.body)
        assertTrue(pages.requested.isEmpty())
    }

    @Test
    fun `a teaser on a feed-only host is shown as stored, never fetched and never checked`() {
        val (service, items, pages) =
            service(FetchedArticle(article(800), "https://img.example/og.jpg"), post(1, "https://github.com/a/b", null))

        assertEquals(post(1, "https://github.com/a/b", null), service.open(OWNER, 1))
        assertTrue(pages.requested.isEmpty())
        assertTrue(items.articles.articleChecks.isEmpty())
        assertEquals(1, items.read.size)
    }

    @Test
    fun `the og image becomes the thumbnail of an item without one`() {
        val (service, items, _) =
            service(FetchedArticle(article(800), "https://img.example/og.jpg"), post(1, "https://blog.example/a", null))

        assertEquals("https://img.example/og.jpg", service.open(OWNER, 1)?.thumbnailUrl)
        val check = items.articles.articleChecks.single()
        assertEquals("https://img.example/og.jpg", check.thumbnailUrl)
    }

    @Test
    fun `the og image never replaces an existing thumbnail`() {
        val withThumbnail =
            post(1, "https://blog.example/a", null).copy(thumbnailUrl = "https://img.example/feed.jpg")
        val (service, _, _) = service(FetchedArticle(article(800), "https://img.example/og.jpg"), withThumbnail)

        assertEquals("https://img.example/feed.jpg", service.open(OWNER, 1)?.thumbnailUrl)
    }
}
