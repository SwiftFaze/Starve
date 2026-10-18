package dev.starve.infra

import dev.starve.application.FeedFetchException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Plain HTTP GETs where the whole exchange, body included, is bounded by one timeout. */
class BoundedHttp(
    private val client: HttpClient,
    private val timeout: Duration,
    private val userAgent: String,
) {
    /** GET [url]; throws [FeedFetchException] when the server cannot be reached or does not answer in time. */
    fun get(
        url: String,
        accept: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpResponse<ByteArray> {
        val builder =
            HttpRequest
                .newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", accept)
        extraHeaders.forEach { (name, value) -> builder.header(name, value) }
        return send(builder.GET().build())
    }

    /** Cancels the request when the timeout expires. */
    private fun send(request: HttpRequest): HttpResponse<ByteArray> {
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
        return try {
            await(future)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw FeedFetchException("timed out after ${inWords(timeout)}", e)
        } catch (e: ExecutionException) {
            throw FeedFetchException(e.cause?.message ?: e.javaClass.simpleName, e)
        }
    }

    /** Waits for the response; an interrupt is restored to the thread and reported as a failed fetch. */
    private fun await(future: Future<HttpResponse<ByteArray>>): HttpResponse<ByteArray> =
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw FeedFetchException("interrupted", e)
        }

    companion object {
        /** A timeout in words, as a source's error shows it: "10 seconds", "1 second", or "500 ms" when not whole. */
        fun inWords(timeout: Duration): String {
            val seconds = timeout.toSeconds()
            return when {
                timeout.toMillisPart() != 0 || seconds == 0L -> "${timeout.toMillis()} ms"
                seconds == 1L -> "1 second"
                else -> "$seconds seconds"
            }
        }
    }
}
