package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FeedRulesAbsoluteUrlTest {
    @Test
    fun `a relative url resolves against its base`() {
        val base = "https://a.test/posts/one"
        assertEquals("https://a.test/images/t.jpg", FeedRules.absoluteHttpUrl("/images/t.jpg", base))
        assertEquals("https://a.test/posts/pic.png", FeedRules.absoluteHttpUrl("pic.png", base))
    }

    @Test
    fun `an absolute url is kept whatever the base`() {
        assertEquals("https://img.test/x.jpg", FeedRules.absoluteHttpUrl("https://img.test/x.jpg", null))
        assertEquals("https://img.test/x.jpg", FeedRules.absoluteHttpUrl("https://img.test/x.jpg", "not a url"))
    }

    @Test
    fun `a relative url without a base is not a usable image`() {
        assertNull(FeedRules.absoluteHttpUrl("/images/t.jpg", null))
    }

    @Test
    fun `only http and https survive, once resolved`() {
        assertNull(FeedRules.absoluteHttpUrl("javascript:alert(1)", "https://a.test/"))
        assertNull(FeedRules.absoluteHttpUrl("data:image/png;base64,AAAA", "https://a.test/"))
        assertNull(FeedRules.absoluteHttpUrl("ftp://a.test/x.jpg", null))
    }

    @Test
    fun `blank and unparseable urls give null, and never the base itself`() {
        assertNull(FeedRules.absoluteHttpUrl(null, "https://a.test/post"))
        assertNull(FeedRules.absoluteHttpUrl("   ", "https://a.test/post"))
        assertNull(FeedRules.absoluteHttpUrl("bad url with spaces.jpg", "https://a.test/post"))
    }
}
