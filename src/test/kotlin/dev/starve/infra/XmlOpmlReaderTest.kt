package dev.starve.infra

import com.sun.net.httpserver.HttpServer
import dev.starve.domain.ImportEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class XmlOpmlReaderTest {
    private val reader = XmlOpmlReader()

    private fun opml(body: String) = """<?xml version="1.0"?><opml version="2.0"><body>$body</body></opml>"""

    @Test
    fun `feeds are titled by title then text then url and ungrouped at the top level`() {
        val parsed =
            requireNotNull(
                reader.read(
                    opml(
                        """<outline title="A" text="ignored" xmlUrl="http://a.test/f"/>
                           <outline text="B" xmlUrl=" https://b.test/f "/>
                           <outline xmlUrl="http://c.test/f"/>""",
                    ),
                ),
            )

        assertEquals(
            listOf(
                ImportEntry("http://a.test/f", "A", null),
                ImportEntry("https://b.test/f", "B", null),
                ImportEntry("http://c.test/f", "http://c.test/f", null),
            ),
            parsed.entries,
        )
    }

    @Test
    fun `folders name their feeds' group by title then text and the nearest folder wins`() {
        val parsed =
            requireNotNull(
                reader.read(
                    opml(
                        """<outline title="Outer"><outline text="Inner"><outline xmlUrl="http://a.test/f"/></outline>
                           <outline xmlUrl="http://b.test/f"/></outline>
                           <outline title="Empty"/>
                           <outline title=""><outline xmlUrl="http://c.test/f"/></outline>""",
                    ),
                ),
            )

        assertEquals(
            listOf("Inner", "Outer", null),
            parsed.entries.map { it.folder },
        )
    }

    @Test
    fun `non http outlines are failed by name`() {
        val feeds = """<outline title="Foo" xmlUrl="ftp://x.test/f"/><outline xmlUrl="nonsense"/>"""
        val parsed = requireNotNull(reader.read(opml(feeds)))

        assertEquals(emptyList<ImportEntry>(), parsed.entries)
        val reasons = listOf("Foo", "nonsense").map { "outline '$it': not an http(s) URL" }
        assertEquals(reasons, parsed.failures)
    }

    @Test
    fun `a document without a body has no entries`() {
        assertEquals(emptyList<ImportEntry>(), reader.read("""<opml version="2.0"/>""")?.entries)
    }

    @Test
    fun `documents that are not opml are rejected`() {
        assertNull(reader.read("""<rss version="2.0"><channel/></rss>"""))
        assertNull(reader.read("<opml><body></opml>"))
        assertNull(reader.read("plain text"))
    }

    @Test
    fun `a doctype is rejected and its external entity is never fetched`() {
        val hits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") {
            hits.incrementAndGet()
            it.sendResponseHeaders(200, 0)
            it.responseBody.close()
        }
        server.start()
        try {
            val url = "http://localhost:${server.address.port}/xxe"
            val text =
                """<?xml version="1.0"?><!DOCTYPE opml [<!ENTITY xxe SYSTEM "$url">]><opml><body>
                |<outline title="&xxe;" xmlUrl="http://a.test/f"/></body></opml>
                """.trimMargin()

            assertNull(reader.read(text))
            assertEquals(0, hits.get())
        } finally {
            server.stop(0)
        }
    }
}
