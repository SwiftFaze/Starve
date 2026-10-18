package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlatformPagesTest {
    private val reddit = PlatformSite(Platform.REDDIT, setOf("reddit.com", "www.reddit.com"), "R/{key}")
    private val github = PlatformSite(Platform.GITHUB, setOf("github.com"), "G/{key}")
    private val apple = PlatformSite(Platform.APPLE_PODCASTS, setOf("podcasts.apple.com"), "A/{key}")
    private val sites = listOf(reddit, github, apple)

    private fun address(url: String) = PlatformPages.match(url, sites)?.address

    @Test
    fun `a subreddit page maps to its feed, with or without a trailing slash or www`() {
        assertEquals("R/selfhosted", address("https://www.reddit.com/r/selfhosted/"))
        assertEquals("R/Kotlin", address("https://reddit.com/R/Kotlin"))
        assertEquals("R/selfhosted", address("  https://www.reddit.com/r/selfhosted?sort=new  "))
    }

    @Test
    fun `other reddit pages are not subreddits`() {
        assertNull(address("https://www.reddit.com/r/selfhosted/comments/abc/title/"))
        assertNull(address("https://www.reddit.com/user/someone/"))
        assertNull(address("https://www.reddit.com/r/selfhosted/.rss"))
        assertNull(address("https://www.reddit.com/r/a/"))
        assertNull(address("https://www.reddit.com/"))
    }

    @Test
    fun `a github user maps to their atom feed, a repository does not`() {
        assertEquals("G/torvalds", address("https://github.com/torvalds"))
        assertEquals("G/some-user", address("https://github.com/some-user/"))
        assertNull(address("https://github.com/torvalds/linux"))
        assertNull(address("https://github.com/torvalds/linux/releases"))
        assertNull(address("https://github.com/torvalds.atom"))
        assertNull(address("https://github.com/"))
    }

    @Test
    fun `an apple podcasts show link maps to the lookup of its id`() {
        assertEquals("A/1528594034", address("https://podcasts.apple.com/us/podcast/hard-fork/id1528594034"))
        assertEquals("A/1528594034", address("https://podcasts.apple.com/podcast/id1528594034?i=1000"))
        assertNull(address("https://podcasts.apple.com/us/podcast/hard-fork/1528594034"))
        assertNull(address("https://podcasts.apple.com/us/browse"))
        assertNull(address("https://podcasts.apple.com/us/channel/id123"))
    }

    @Test
    fun `pages on other hosts and unparseable urls are left to discovery`() {
        assertNull(address("https://bsky.app/profile/someone.bsky.social"))
        assertNull(address("https://mastodon.social/@someone"))
        assertNull(address("https://someone.substack.com/"))
        assertNull(address("https://gitlab.com/torvalds"))
        assertNull(address("not a url at all"))
        assertNull(address("mailto:someone"))
    }

    @Test
    fun `hosts match ignoring case, and the first site on the host that recognises the page wins`() {
        assertEquals("G/torvalds", address("https://GitHub.com/torvalds"))
        val shared = listOf(github.copy(hosts = setOf("x.test")), reddit.copy(hosts = setOf("x.test")))
        val page = PlatformPages.match("https://x.test/someone", shared)
        assertEquals(Platform.GITHUB, page?.site?.platform)
        assertEquals("someone", page?.key)
        assertEquals(Platform.REDDIT, PlatformPages.match("https://x.test/r/kotlin", shared)?.site?.platform)
    }
}
