package dev.starve.infra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BodySanitizerTest {
    private fun clean(
        html: String?,
        base: String? = "https://blog.example/post/",
    ) = BodySanitizer.sanitize(html, base)

    @Test
    fun `structural markup survives and output is compact`() {
        val html = "<h2>T</h2><p>a <b>b</b> <code>c</code></p><ul><li>x</li></ul><blockquote>q</blockquote>"

        assertEquals(html, clean(html))
    }

    @Test
    fun `figures, tables and images survive`() {
        val html = "<figure><img src=\"https://i.example/a.png\" alt=\"a\"><figcaption>cap</figcaption></figure>"

        assertEquals(html, clean(html))
        assertTrue(clean("<table><tbody><tr><td>1</td></tr></tbody></table>")!!.contains("<td>1</td>"))
    }

    @Test
    fun `active elements go with their content`() {
        val html =
            "<p>Safe</p><script>alert(1)</script><iframe src=\"https://e.example\">hidden</iframe>" +
                "<object data=\"x\">o</object><embed src=\"x\"><form action=\"/x\">f<input name=\"a\"></form>" +
                "<style>p{}</style><link rel=\"stylesheet\" href=\"https://e.example/c.css\">"

        assertEquals("<p>Safe</p>", clean(html))
    }

    @Test
    fun `event handlers and styles are removed but the element stays`() {
        val cleaned = clean("<p onclick=\"x()\" style=\"color:red\" ONMOUSEOVER=\"y()\">T</p>")

        assertEquals("<p>T</p>", cleaned)
    }

    @Test
    fun `only http, https and mailto links and http and https images survive`() {
        val html =
            "<a href=\"javascript:x()\">a</a><a href=\"JaVaScRiPt:x()\">b</a><a href=\"data:text/html,x\">c</a>" +
                "<a href=\"ftp://f.example/x\">d</a><a href=\"mailto:me@example.com\">e</a>" +
                "<img src=\"javascript:x()\"><img src=\"data:image/png;base64,AAAA\">" +
                "<img src=\"https://i.example/a.png\">"

        val cleaned = clean(html)!!

        assertFalse(cleaned.contains("javascript", ignoreCase = true))
        assertFalse(cleaned.contains("data:"))
        assertFalse(cleaned.contains("ftp:"))
        assertTrue(cleaned.contains("href=\"mailto:me@example.com\""))
        assertTrue(cleaned.contains("src=\"https://i.example/a.png\""))
    }

    @Test
    fun `every link opens in a new tab without the opener`() {
        val cleaned = clean("<a href=\"https://o.example/\" target=\"_self\" rel=\"opener\">x</a>")

        assertEquals(
            "<a href=\"https://o.example/\" rel=\"noopener noreferrer\" target=\"_blank\">x</a>",
            cleaned,
        )
    }

    @Test
    fun `relative urls resolve against the item link and are dropped without one`() {
        val html = "<a href=\"/about\">a</a><a href=\"more.html\">m</a><img src=\"/i/a.png\">"

        val resolved = clean(html)!!
        assertTrue(resolved.contains("href=\"https://blog.example/about\""))
        assertTrue(resolved.contains("href=\"https://blog.example/post/more.html\""))
        assertTrue(resolved.contains("src=\"https://blog.example/i/a.png\""))

        val dropped = clean(html, null)!!
        assertFalse(dropped.contains("href"))
        assertFalse(dropped.contains("src"))
    }

    @Test
    fun `nothing, blanks and bodies that clean to nothing are no body`() {
        assertNull(clean(null))
        assertNull(clean("   "))
        assertNull(clean("<script>x()</script>"))
        assertNull(clean("<!-- only a comment -->"))
    }

    @Test
    fun `an oversized body is capped before cleaning and stays well formed and idempotent`() {
        val cleaned = clean("<p>" + "a".repeat(500_000) + "</p>")!!

        assertTrue(cleaned.length <= 200_000)
        assertTrue(cleaned.endsWith("</p>"))
        assertEquals(cleaned, clean(cleaned))
    }

    @Test
    fun `cleaning twice changes nothing`() {
        val once =
            clean(
                "<p onclick=\"x()\">Hi &amp; <a href=\"/a\">there</a> &nbsp;<br>x</p><script>x()</script>" +
                    "<img src=\"/i.png\" alt=\"q&quot;\"><h1>unclosed",
            )

        assertEquals(once, clean(once))
    }
}
