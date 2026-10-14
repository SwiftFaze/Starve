package dev.starve.infra

import com.sun.net.httpserver.HttpServer
import dev.starve.application.ArticleFetcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.http.HttpClient
import java.time.Duration

class HttpArticleFetcherTest {
    private lateinit var server: HttpServer
    private lateinit var fetcher: ArticleFetcher

    private val url get() = "http://127.0.0.1:${server.address.port}/post"

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.start()
        fetcher = HttpArticleFetcher(BoundedHttp(HttpClient.newHttpClient(), Duration.ofSeconds(10), "test"))
    }

    @AfterEach
    fun tearDown() = server.stop(0)

    private fun serve(
        status: Int,
        contentType: String,
        body: ByteArray,
    ) {
        server.createContext("/post") { exchange ->
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    private fun page(bytes: Int): ByteArray {
        val frame = "<html><body><article><p></p></article></body></html>".length
        return "<html><body><article><p>${"x".repeat(bytes - frame)}</p></article></body></html>".toByteArray()
    }

    private val small = "<html><body><article><p>Hello</p></article></body></html>".toByteArray()

    @Test
    fun `an html page is extracted into its article`() {
        serve(200, "text/html; charset=utf-8", small)

        assertEquals("<p>Hello</p>", fetcher.fetch(url)?.body)
    }

    @Test
    fun `the content type is matched whatever its case`() {
        serve(200, "TEXT/HTML", small)

        assertEquals("<p>Hello</p>", fetcher.fetch(url)?.body)
    }

    @Test
    fun `a non-html answer is a failed fetch`() {
        serve(200, "application/pdf", small)

        assertNull(fetcher.fetch(url))
    }

    @Test
    fun `an html answer without a content type is a failed fetch`() {
        server.createContext("/post") { exchange ->
            exchange.sendResponseHeaders(200, small.size.toLong())
            exchange.responseBody.use { it.write(small) }
        }

        assertNull(fetcher.fetch(url))
    }

    @Test
    fun `an error status is a failed fetch even with an html body`() {
        serve(404, "text/html", small)

        assertNull(fetcher.fetch(url))
    }

    @Test
    fun `a redirect status that is not followed is a failed fetch`() {
        serve(300, "text/html", small)

        assertNull(fetcher.fetch(url))
    }

    @Test
    fun `the last 2xx status is still a page`() {
        serve(299, "text/html", small)

        assertNotNull(fetcher.fetch(url))
    }

    @Test
    fun `a page of exactly the size limit is read`() {
        serve(200, "text/html", page(1_000_000))

        assertNotNull(fetcher.fetch(url)?.body)
    }

    @Test
    fun `a page one byte over the size limit is a failed fetch`() {
        serve(200, "text/html", page(1_000_001))

        assertNull(fetcher.fetch(url))
    }

    @Test
    fun `a page that cannot be reached is a failed fetch`() {
        val closed = ServerSocket(0).use { it.localPort }

        assertNull(fetcher.fetch("http://127.0.0.1:$closed/post"))
    }
}
