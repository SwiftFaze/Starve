package dev.starve.infra

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArticleExtractorTest {
    private val link = "https://blog.example/posts/one"

    private fun extract(html: String) = ArticleExtractor.extract(Jsoup.parse(html, link), link)

    @Test
    fun `the article element is the main text`() {
        val body = extract("<body><div><p>Side note.</p></div><article><p>Main text.</p></article></body>").body

        assertEquals("<p>Main text.</p>", body)
    }

    @Test
    fun `the main element is used when there is no article`() {
        val body = extract("<body><p>Side note.</p><main><p>Main text.</p></main></body>").body

        assertEquals("<p>Main text.</p>", body)
    }

    @Test
    fun `the block with the most paragraph text is used when there is neither`() {
        val body =
            extract(
                "<body><div><p>Short.</p></div>" +
                    "<div><p>The long paragraph of the article itself.</p><p>And another one.</p></div></body>",
            ).body

        assertTrue(body!!.contains("The long paragraph of the article itself."))
        assertFalse(body.contains("Short."))
    }

    @Test
    fun `navigation, header, footer, aside, form and script outside the article are dropped`() {
        val page =
            "<body><nav>Site navigation</nav><header>Masthead</header><aside>Related</aside>" +
                "<article><p>Main text.</p><form>Subscribe</form><script>var x = 1;</script></article>" +
                "<footer>Copyright footer</footer></body>"

        val body = extract(page).body!!

        assertEquals("<p>Main text.</p>", body)
    }

    @Test
    fun `relative links and images resolve against the item link`() {
        val page = "<body><article><p><a href=\"/about\">About</a> <img src=\"pic.png\"></p></article></body>"
        val body = extract(page).body!!

        assertTrue(body.contains("href=\"https://blog.example/about\""))
        assertTrue(body.contains("src=\"https://blog.example/posts/pic.png\""))
    }

    @Test
    fun `a page without text has no body but may still have an og image`() {
        val page =
            extract("<head><meta property=\"og:image\" content=\"https://img.example/og.jpg\"></head><body></body>")

        assertNull(page.body)
        assertEquals("https://img.example/og.jpg", page.imageUrl)
    }

    @Test
    fun `a relative og image resolves against the page and a non-http one is ignored`() {
        val relative = extract("<head><meta property=\"og:image\" content=\"/img/og.jpg\"></head>")
        val script = extract("<head><meta property=\"og:image\" content=\"javascript:alert(1)\"></head>")

        assertEquals("https://blog.example/img/og.jpg", relative.imageUrl)
        assertNull(script.imageUrl)
    }
}
