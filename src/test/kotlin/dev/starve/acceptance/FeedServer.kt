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

    data object Stall : Route
}

data class RecordedRequest(
    val path: String,
    val ifNoneMatch: String?,
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

    fun routeOf(path: String): Route? = routes[path]

    fun stop() {
        server.stop(0)
        executor.shutdownNow()
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val ifNoneMatch = exchange.requestHeaders.getFirst("If-None-Match")
        seen += RecordedRequest(path, ifNoneMatch)
        when (val route = routes[path]) {
            null -> respond(exchange, 404, "text/plain", "not found")
            is Route.Status -> respond(exchange, route.code, "text/plain", "status ${route.code}")
            is Route.Raw -> respond(exchange, 200, route.contentType, resource(route.resource))
            is Route.Feed -> serveFeed(exchange, path, route.spec, ifNoneMatch)
            Route.Stall -> stall(exchange)
        }
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
        private const val STALL_MILLIS = 5_000L
    }
}
