package dev.starve.infra

import dev.starve.application.FeedFetchException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.ServerSocket
import java.net.http.HttpClient
import java.time.Duration
import kotlin.concurrent.thread

class BoundedHttpTest {
    private val http = BoundedHttp(HttpClient.newHttpClient(), Duration.ofSeconds(2), "test-agent")

    @Test
    fun `a failure that carries a message reports that message`() {
        ServerSocket(0).use { socket ->
            thread(isDaemon = true) {
                socket.accept().use { it.getOutputStream().apply { write("garbage\r\n\r\n".toByteArray()) }.flush() }
            }
            val failure = assertThrows<FeedFetchException> { http.get("http://localhost:${socket.localPort}/", "*/*") }
            assertEquals("Invalid status line: \"garbage\"", failure.message)
        }
    }

    @Test
    fun `a failure without a message reports the exception type`() {
        val port = ServerSocket(0).use { it.localPort }
        val failure = assertThrows<FeedFetchException> { http.get("http://localhost:$port/", "*/*") }
        assertEquals("ExecutionException", failure.message)
    }
}
