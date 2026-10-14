package dev.starve.acceptance

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dev.starve.StarveApplication
import org.slf4j.LoggerFactory
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.core.simple.JdbcClient
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.sql.DataSource

private const val CSRF_COOKIE = "XSRF-TOKEN"
private const val CSRF_HEADER = "X-CSRF-TOKEN"
private val SETUP_CODE_LINE = Regex("Starve setup code: ([A-Z2-9]{4}-[A-Z2-9]{4})")

/** Starts, stops and queries the real application against one temporary SQLite file. */
class ApplicationHarness(
    private val oembedUrl: () -> String,
    private val soundCloudOembedUrl: () -> String,
) {
    private var dbFile: Path = newDbFile()
    private var context: ConfigurableApplicationContext? = null
    private var extraArgs: List<String> = emptyList()
    private val logs = ListAppender<ILoggingEvent>().apply { start() }
    private val otherClients = mutableMapOf<String, Browser>()

    /** Holds the post-import polls of the running application until a step releases them. */
    var importRunner = ManualRunner()
        private set

    /** The test's own browser: no forwarded address, so the application sees localhost. */
    var browser = Browser(null)
        private set

    /** The account the default browser completed setup as, if it is still signed in. */
    private var signedInAs: Pair<String, String>? = null

    /** Drives the login block: the clock is the real one plus whatever scenarios advanced it by. */
    val clock = MutableClock()

    val dbPath: Path get() = dbFile

    fun freshDatabase() {
        stop()
        extraArgs = emptyList()
        dbFile = newDbFile()
        browser = Browser(null)
        signedInAs = null
        otherClients.clear()
        clock.reset()
    }

    fun start() {
        check(context == null) { "application already running" }
        val runner = ManualRunner().also { importRunner = it }
        logs.list.clear()
        context =
            SpringApplicationBuilder(StarveApplication::class.java)
                .initializers(useRunner(runner), useClock(), useLogCapture())
                .run(
                    "--server.port=0",
                    "--starve.db.path=$dbFile",
                    "--starve.scheduling.enabled=false",
                    "--starve.fetch.timeout=PT1S",
                    "--starve.youtube.oembed-url=${oembedUrl()}",
                    "--starve.soundcloud.oembed-url=${soundCloudOembedUrl()}",
                    *extraArgs.toTypedArray(),
                )
    }

    fun stop() {
        context?.close()
        context = null
        rootLogger().detachAppender(logs)
    }

    /** Restarts on the same database with extra `--property=value` arguments, kept until the next fresh database. */
    fun restartWith(vararg args: String) {
        extraArgs = args.toList()
        restart()
    }

    /**
     * A restart ends every session (sessions live in memory), so the default browser signs back in as the account it
     * was last signed in as. That keeps "Given the application is running" meaning signed in after a restart.
     */
    fun restart() {
        stop()
        start()
        signedInAs?.let { (username, password) ->
            browser.postForm(
                "/login",
                emptyMap(),
                listOf(
                    "username" to username,
                    "password" to password,
                ),
            )
        }
    }

    /** Stops the default browser being signed back in after a restart, as when it signed out. */
    fun forgetSignIn() {
        signedInAs = null
    }

    /** Another client, identified to the application by [address] through X-Forwarded-For. */
    fun browserAt(address: String): Browser = otherClients.getOrPut(address) { Browser(address) }

    /** The log lines of the current run, since the last start. */
    fun loggedLines(): List<String> = logs.list.map { it.formattedMessage }

    /** The setup code logged by the current run, or null when none was logged. */
    fun loggedSetupCode(): String? {
        val codes = loggedLines().mapNotNull { SETUP_CODE_LINE.find(it)?.groupValues?.get(1) }
        return codes.firstOrNull()
    }

    /** Completes first-run setup with this run's logged code; the default browser is then signed in as the admin. */
    fun completeSetup(
        username: String,
        password: String,
    ): HttpResponse<String> {
        val code = requireNotNull(loggedSetupCode()) { "no setup code in this run's log" }
        val response = postSetup(code, username, password)
        check(response.statusCode() == 302) { "setup did not complete: ${response.statusCode()} ${response.body()}" }
        signedInAs = username to password
        return response
    }

    fun postSetup(
        code: String,
        username: String,
        password: String,
        from: Browser = browser,
    ): HttpResponse<String> {
        val fields = listOf("code" to code, "username" to username, "password" to password)
        return from.postForm("/setup", emptyMap(), fields)
    }

    fun get(
        path: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> = browser.get(path, headers)

    fun postForm(
        path: String,
        vararg fields: Pair<String, String>,
    ): HttpResponse<String> = postForm(path, emptyMap(), *fields)

    fun postForm(
        path: String,
        headers: Map<String, String>,
        vararg fields: Pair<String, String>,
    ): HttpResponse<String> = browser.postForm(path, headers, fields.toList())

    /** A form post as a browser sends it when the page had no CSRF token, which the application must refuse. */
    fun postFormWithoutCsrfToken(
        path: String,
        vararg fields: Pair<String, String>,
    ): HttpResponse<String> = browser.postForm(path, emptyMap(), fields.toList(), csrf = false)

    /** Uploads [content] as the multipart file field [field] named [filename], with the form's CSRF token. */
    fun postFile(
        path: String,
        field: String,
        filename: String,
        content: ByteArray,
    ): HttpResponse<String> = browser.postFile(path, field, filename, content)

    fun <T : Any> bean(type: Class<T>): T = running().getBean(type)

    fun jdbc(): JdbcClient = JdbcClient.create(running().getBean(DataSource::class.java))

    fun cleanUp() {
        stop()
        Files.deleteIfExists(dbFile)
    }

    private fun useRunner(runner: ManualRunner) =
        ApplicationContextInitializer<ConfigurableApplicationContext> {
            it.beanFactory.registerSingleton("backgroundRunner", runner)
        }

    /** Attached by an initializer, because Boot resets Logback when it starts and would drop an earlier appender. */
    private fun useLogCapture() =
        ApplicationContextInitializer<ConfigurableApplicationContext> {
            rootLogger().addAppender(logs)
        }

    private fun useClock() =
        ApplicationContextInitializer<ConfigurableApplicationContext> {
            it.beanFactory.registerSingleton("clock", clock)
        }

    private fun running() = requireNotNull(context) { "application not running" }

    private fun newDbFile(): Path = Files.createTempDirectory("starve-test").resolve("starve.db")

    private fun rootLogger() = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

    /** One HTTP client with its own cookies, as one browser has. Redirects are never followed. */
    inner class Browser(
        private val forwardedFor: String?,
    ) {
        private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        private val http = HttpClient.newBuilder().cookieHandler(cookies).build()

        fun get(
            path: String,
            headers: Map<String, String> = emptyMap(),
        ): HttpResponse<String> = send(HttpRequest.newBuilder(uri(path)).GET(), headers)

        fun postForm(
            path: String,
            headers: Map<String, String>,
            fields: List<Pair<String, String>>,
            csrf: Boolean = true,
        ): HttpResponse<String> {
            val body = fields.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
            val builder =
                HttpRequest
                    .newBuilder(uri(path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
            if (csrf) csrfToken()?.let { builder.header(CSRF_HEADER, it) }
            return send(builder, headers)
        }

        /** The file goes in a multipart form that carries `_csrf`, as the page's own upload form does. */
        fun postFile(
            path: String,
            field: String,
            filename: String,
            content: ByteArray,
        ): HttpResponse<String> {
            val boundary = "starve-boundary-${System.nanoTime()}"
            val token = csrfToken().orEmpty()
            val csrfPart = "--$boundary\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n$token\r\n"
            val head = "--$boundary\r\nContent-Disposition: form-data; name=\"$field\"; filename=\"$filename\"\r\n"
            val part = "Content-Type: application/octet-stream\r\n\r\n"
            val body = (csrfPart + head + part).toByteArray() + content + "\r\n--$boundary--\r\n".toByteArray()
            val builder =
                HttpRequest
                    .newBuilder(uri(path))
                    .header("Content-Type", "multipart/form-data; boundary=$boundary")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            return send(builder, emptyMap())
        }

        /** Forgets every cookie. */
        fun clearCookies() {
            cookies.cookieStore.removeAll()
        }

        /** What a browser keeps when it closes: cookies without an expiry go, persistent ones stay. */
        fun endSession() {
            val kept = cookies.cookieStore.cookies.filter { it.maxAge > 0 }
            cookies.cookieStore.removeAll()
            kept.forEach { cookie -> cookies.cookieStore.add(URI.create("http://localhost/"), cookie) }
        }

        /**
         * The CSRF token the application last set. A browser that has loaded no page yet fetches /login first, which
         * sets the cookie on any state (login, setup or the redirect to setup), as a real browser would have one.
         */
        private fun csrfToken(): String? {
            if (cookieToken() == null) send(HttpRequest.newBuilder(uri("/login")).GET(), emptyMap())
            return cookieToken()
        }

        private fun cookieToken(): String? =
            cookies.cookieStore.cookies
                .firstOrNull { it.name == CSRF_COOKIE }
                ?.value

        private fun send(
            builder: HttpRequest.Builder,
            headers: Map<String, String>,
        ): HttpResponse<String> {
            headers.forEach { (k, v) -> builder.header(k, v) }
            forwardedFor?.let { builder.header("X-Forwarded-For", it) }
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }

        private fun uri(path: String): URI {
            val port = running().environment.getProperty("local.server.port")
            return URI.create("http://localhost:$port$path")
        }

        private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
    }
}

/** The real clock, moved forward only by the scenarios that advance it. */
class MutableClock : Clock() {
    private var offset: Duration = Duration.ZERO

    fun advance(by: Duration) {
        offset = offset.plus(by)
    }

    fun reset() {
        offset = Duration.ZERO
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = Instant.now().plus(offset)
}
