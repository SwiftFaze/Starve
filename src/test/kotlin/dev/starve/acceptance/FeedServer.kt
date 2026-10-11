package dev.starve.acceptance

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/** What a fixture feed looks like; rendered fresh on every request. */
data class FeedSpec(
    val atom: Boolean = false,
    val title: String? = "Feed",
    val entries: Int = 1,
    val thumbnails: Boolean = false,
    val noDate: Boolean = false,
    val noGuid: Boolean = false,
    val etag: Boolean = false,
)

sealed interface Route {
    data class Feed(
        val spec: FeedSpec,
    ) : Route

    data class Status(
        val code: Int,
    ) : Route

    data class Raw(
        val resource: String,
        val contentType: String,
    ) : Route

    /** An HTML page with the given markup. */
    data class Html(
        val body: String,
    ) : Route

    data object Stall : Route

    /** Sends headers and a little body, then stalls. */
    data object StallBody : Route
}

data class RecordedRequest(
    val path: String,
    val ifNoneMatch: String?,
    val query: String? = null,
    val userAgent: String? = null,
)

/** An in-process HTTP server on an ephemeral port serving feed fixtures. */
class FeedServer {
    private val routes = ConcurrentHashMap<String, Route>()
    private val seen = CopyOnWriteArrayList<RecordedRequest>()
    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)

    val baseUrl: String get() = "http://localhost:${server.address.port}"
    val requests: List<RecordedRequest> get() = seen.toList()

    init {
        server.executor = executor
        server.createContext("/") { handle(it) }
        server.start()
    }

    fun route(
        path: String,
        route: Route,
    ) {
        routes[path] = route
    }

    /** Answers the YouTube oEmbed query for [videoId] (served at /oembed) with [status]. */
    fun routeOembed(
        videoId: String,
        status: Int,
    ) = route("$OEMBED_PATH/$videoId", Route.Status(status))

    fun clearRoutes() = routes.clear()

    fun routeOf(path: String): Route? = routes[path]

    fun stop() {
        server.stop(0)
        executor.shutdownNow()
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val ifNoneMatch = exchange.requestHeaders.getFirst("If-None-Match")
        val userAgent = exchange.requestHeaders.getFirst("User-Agent")
        seen += RecordedRequest(path, ifNoneMatch, exchange.requestURI.query, userAgent)
        when (val route = routes[routeKey(exchange)]) {
            null -> respond(exchange, 404, "text/plain", "not found")
            is Route.Status -> respond(exchange, route.code, "text/plain", "status ${route.code}")
            is Route.Html -> respond(exchange, 200, "text/html; charset=utf-8", route.body)
            is Route.Raw -> respond(exchange, 200, route.contentType, resource(route.resource))
            is Route.Feed -> serveFeed(exchange, path, route.spec, ifNoneMatch)
            Route.Stall -> stall(exchange)
            Route.StallBody -> stallBody(exchange)
        }
    }

    /** oEmbed queries are routed per video; everything else by path. */
    private fun routeKey(exchange: HttpExchange): String {
        val path = exchange.requestURI.path
        val videoId = OEMBED_VIDEO.find(exchange.requestURI.rawQuery.orEmpty())?.groupValues?.get(1)
        return if (path == OEMBED_PATH && videoId != null) "$OEMBED_PATH/$videoId" else path
    }

    private fun serveFeed(
        exchange: HttpExchange,
        path: String,
        spec: FeedSpec,
        ifNoneMatch: String?,
    ) {
        if (spec.etag) exchange.responseHeaders.add("ETag", ETAG)
        if (spec.etag && ifNoneMatch == ETAG) {
            respond(exchange, 304, "application/xml", "")
        } else {
            respond(exchange, 200, "application/xml", FeedXml.render(baseUrl, path, spec))
        }
    }

    private fun stallBody(exchange: HttpExchange) {
        exchange.sendResponseHeaders(200, BODY_CLAIM)
        exchange.responseBody.write("<rss".toByteArray())
        exchange.responseBody.flush()
        stall(exchange)
    }

    private fun stall(exchange: HttpExchange) {
        try {
            Thread.sleep(STALL_MILLIS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        exchange.close()
    }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        contentType: String,
        body: String,
    ) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(status, if (status == 304) -1 else bytes.size.toLong())
        if (status != 304) exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }

    private fun resource(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/feeds/$name")) { "missing fixture $name" }
            .use { String(it.readBytes()) }

    companion object {
        const val ETAG = "\"v1\""
        const val OEMBED_PATH = "/oembed"
        private val OEMBED_VIDEO = Regex("""url=[^&]*watch%3Fv%3D([A-Za-z0-9_-]+)""")
        private const val STALL_MILLIS = 5_000L
        private const val BODY_CLAIM = 100_000L
    }
}
