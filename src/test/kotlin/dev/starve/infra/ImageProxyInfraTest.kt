package dev.starve.infra

import dev.starve.acceptance.FeedServer
import dev.starve.acceptance.Route
import dev.starve.application.ProxiedImage
import dev.starve.domain.FeedItem
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.simple.JdbcClient
import java.net.InetAddress
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ImageProxyInfraTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var server: FeedServer

    @BeforeEach
    fun setUp() {
        server = FeedServer()
    }

    @AfterEach
    fun tearDown() = server.stop()

    private fun fetcher(
        trusted: Set<String> = setOf("localhost"),
        resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    ): HttpImageFetcher {
        val timeout = Duration.ofSeconds(2)
        val client =
            HttpClient
                .newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        return HttpImageFetcher(BoundedHttp(client, timeout, "test"), trusted, resolve)
    }

    // ---- fetching -----------------------------------------------------------------------

    @Test
    fun `an image is fetched with its media type`() {
        server.route("/a.png", Route.Icon)

        val image = fetcher().fetch("${server.baseUrl}/a.png")

        assertEquals("image/png", image?.contentType)
        assertArrayEquals("icon".toByteArray(), image?.bytes)
    }

    @Test
    fun `redirects are followed up to three hops, and a fourth is refused`() {
        server.route("/a.png", Route.Icon)
        server.route("/r1", Route.Redirect(302, "${server.baseUrl}/a.png"))
        server.route("/r2", Route.Redirect(301, "/r1"))
        server.route("/r3", Route.Redirect(307, "/r2"))
        server.route("/r4", Route.Redirect(308, "/r3"))

        assertEquals("image/png", fetcher().fetch("${server.baseUrl}/r3")?.contentType)
        assertNull(fetcher().fetch("${server.baseUrl}/r4"))
    }

    @Test
    fun `a redirect without a usable location is refused`() {
        server.route("/r", Route.Redirect(302, null))
        server.route("/bad", Route.Redirect(302, "javascript:alert(1)"))

        assertNull(fetcher().fetch("${server.baseUrl}/r"))
        assertNull(fetcher().fetch("${server.baseUrl}/bad"))
    }

    @Test
    fun `a page, an error status or an unreachable host is no image`() {
        server.route("/page", Route.Html("<p>hi</p>"))
        server.route("/gone", Route.Status(404))
        server.route("/slow", Route.Stall)

        assertNull(fetcher().fetch("${server.baseUrl}/page"))
        assertNull(fetcher().fetch("${server.baseUrl}/gone"))
        assertNull(fetcher().fetch("${server.baseUrl}/slow"))
        assertNull(fetcher().fetch("ftp://localhost/a.png"))
    }

    @Test
    fun `a local host is refused unless trusted, and so is a redirect onto one`() {
        server.route("/a.png", Route.Icon)
        val numeric = server.baseUrl.replace("localhost", "127.0.0.1")
        server.route("/out", Route.Redirect(302, "$numeric/a.png"))

        assertNull(fetcher(trusted = emptySet()).fetch("${server.baseUrl}/a.png"))
        assertNull(fetcher().fetch("$numeric/a.png"))
        assertNull(fetcher().fetch("${server.baseUrl}/out"))
        assertEquals("image/png", fetcher().fetch("${server.baseUrl}/a.png")?.contentType)
    }

    @Test
    fun `a host whose addresses are all public is fetched, one with any local address is not`() {
        val asked = mutableListOf<String>()
        val mixed =
            fetcher(trusted = emptySet()) {
                asked += it
                listOf(InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.1"))
            }
        val unknown = fetcher(trusted = emptySet()) { throw java.net.UnknownHostException(it) }
        val none = fetcher(trusted = emptySet()) { emptyList() }

        assertNull(mixed.fetch("http://mixed.example/a.png"))
        assertEquals(listOf("mixed.example"), asked)
        assertNull(unknown.fetch("http://unknown.example/a.png"))
        assertNull(none.fetch("http://none.example/a.png"))
    }

    // ---- the disk cache -----------------------------------------------------------------

    private val clock = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `a stored image is found again with its type, and an unknown one is not`() {
        val cache = DiskImageCache(tmp.resolve("images"), clock)

        cache.store("https://a.example/x.png", ProxiedImage(byteArrayOf(1, 2, 3), "image/png"))
        cache.store("https://a.example/f.ico", ProxiedImage(byteArrayOf(4), "image/vnd.microsoft.icon"))

        val found = cache.find("https://a.example/x.png")
        assertArrayEquals(byteArrayOf(1, 2, 3), found?.bytes)
        assertEquals("image/png", found?.contentType)
        assertEquals("image/x-icon", cache.find("https://a.example/f.ico")?.contentType)
        assertNull(cache.find("https://a.example/other.png"))
        assertNull(DiskImageCache(tmp.resolve("none"), clock).find("https://a.example/x.png"))
    }

    @Test
    fun `pruning removes what is no longer referenced, then the least recently used over the cap`() {
        val dir = tmp.resolve("images")
        listOf("old", "mid", "new", "gone").forEachIndexed { index, name ->
            val storedAt = Clock.fixed(clock.instant().plusSeconds(index.toLong()), ZoneOffset.UTC)
            DiskImageCache(dir, storedAt).store("https://a.example/$name.png", ProxiedImage(ByteArray(10), "image/png"))
        }
        val cache = DiskImageCache(dir, clock)

        val removed = cache.prune(setOf("old", "mid", "new").map { "https://a.example/$it.png" }.toSet(), 20)

        assertEquals(2, removed)
        assertNull(cache.find("https://a.example/gone.png"))
        assertNull(cache.find("https://a.example/old.png"))
        assertEquals(10, cache.find("https://a.example/mid.png")?.bytes?.size)
        assertEquals(10, cache.find("https://a.example/new.png")?.bytes?.size)
        assertEquals(0, DiskImageCache(tmp.resolve("none"), clock).prune(emptySet(), 0))
    }

    @Test
    fun `an image of a type it cannot name is not stored`() {
        val cache = DiskImageCache(tmp.resolve("images"), clock)

        cache.store("https://a.example/x.tif", ProxiedImage(byteArrayOf(1), "image/tiff"))

        assertNull(cache.find("https://a.example/x.tif"))
    }

    // ---- links and references -----------------------------------------------------------

    @Test
    fun `post html has its images and links rewritten and nothing else`() {
        val html = "<p>Hi <a href=\"https://a.example/?utm_source=x\">a</a></p><img src=\"https://a.example/i.png\" alt=\"i\">"

        val rewritten = JsoupHtmlLinks.rewrite(html, { "/img/$it" }, { it.substringBefore('?') })

        assertEquals(
            "<p>Hi <a href=\"https://a.example/\">a</a></p>\n<img src=\"/img/https://a.example/i.png\" alt=\"i\">",
            rewritten,
        )
    }

    @Test
    fun `the referenced images are the thumbnails, favicons and body images of stored rows`() {
        val dataSource = DatabaseConfig().dataSource(tmp.resolve("t.db").toString())
        Flyway
            .configure()
            .dataSource(dataSource)
            .javaMigrations(SearchIndexMigration())
            .load()
            .migrate()
        val jdbc = JdbcClient.create(dataSource)
        jdbc.seedOwners()
        val sources = JdbcSourceRepository(jdbc, SearchIndex(jdbc))
        val items = JdbcItemRepository(jdbc, SearchIndex(jdbc))
        val source = sources.insert(OWNER, "http://a.test/f", "A")
        sources.recordFavicon(source.id, "https://a.test/icon.png", seen())
        val body = "<p>x</p><img src=\"https://cdn.test/b1.png\"><img src=\"https://cdn.test/b2.png\">"
        items.insertNew(
            source.id,
            listOf(
                FeedItem("1", "One", null, seen(), null, "https://cdn.test/t.png", body),
                FeedItem("2", "Two", null, seen(), null, null, "<p>no images</p>"),
            ),
            seen(),
        )

        assertEquals(
            setOf("https://a.test/icon.png", "https://cdn.test/t.png", "https://cdn.test/b1.png", "https://cdn.test/b2.png"),
            JdbcImageReferences(jdbc).all(),
        )
        (dataSource as AutoCloseable).close()
    }

    private fun seen() = Instant.parse("2026-10-18T12:00:00Z")
}
