package dev.starve.acceptance

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dev.starve.StarveApplication
import dev.starve.application.SourceRepository
import dev.starve.application.UserRepository
import dev.starve.domain.Source
import dev.starve.infra.DecodeItemTitlesMigration
import dev.starve.infra.SearchIndexMigration
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.slf4j.LoggerFactory
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
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

    /** The passwords of the accounts scenarios can sign in as: the owner, plus every user seeded by [seedUser]. */
    private val passwords = mutableMapOf(World.OWNER to World.OWNER_PASSWORD)

    /** Set when the database is a legacy one that predates accounts: it needs an owner before the app starts. */
    private var legacyUpgrade = false
    private var legacyOwnerSeeded = false

    /** Drives the login block: the clock is the real one plus whatever scenarios advanced it by. */
    val clock = MutableClock()

    val dbPath: Path get() = dbFile

    fun freshDatabase() {
        stop()
        extraArgs = emptyList()
        dbFile = newDbFile()
        browser = Browser(null)
        signedInAs = null
        legacyUpgrade = false
        legacyOwnerSeeded = false
        passwords.keys.retainAll(setOf(World.OWNER))
        otherClients.clear()
        clock.reset()
    }

    /** Marks the database as one an older release left, so [start] can give its rows an owner first. */
    fun markLegacy() {
        legacyUpgrade = true
    }

    /**
     * Sources and groups need an owner, and accounts do not exist before V13. So a legacy database is migrated up
     * to the accounts table first (still running every older migration over its rows), and the owner is created
     * there; the application's own start then adds the per-user schema and gives the rows to that owner.
     */
    private fun upgradeLegacyDatabase() {
        val dataSource = SimpleDriverDataSource(org.sqlite.JDBC(), "jdbc:sqlite:$dbFile")
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .javaMigrations(DecodeItemTitlesMigration(), SearchIndexMigration())
            .target(MigrationVersion.fromVersion("13"))
            .load()
            .migrate()
        JdbcClient
            .create(dataSource)
            .sql("INSERT INTO users (username, password_hash, admin, created_at) VALUES (?, ?, 1, 0)")
            .params(World.OWNER, BCryptPasswordEncoder(BCRYPT_COST).encode(World.OWNER_PASSWORD))
            .update()
        legacyUpgrade = false
        legacyOwnerSeeded = true
    }

    /** Starts the application and leaves the default browser signed in as the owner (set up, or already seeded). */
    fun startAsOwner() {
        start()
        if (legacyOwnerSeeded) signIn(World.OWNER) else completeSetup(World.OWNER, World.OWNER_PASSWORD)
    }

    /** Creates a non-admin account through the user repository; signup is not built, so the harness seeds one. */
    fun seedUser(username: String) {
        val hash = bean(PasswordEncoder::class.java).encode(MEMBER_PASSWORD)
        bean(UserRepository::class.java).insert(username, hash, clock.instant())
        passwords[username] = MEMBER_PASSWORD
    }

    /**
     * Replaces the test client with a fresh one signed in as [username]. It posts the login form like any browser;
     * a successful sign-in is never throttled, and the client then keeps that account's session cookie.
     */
    fun signIn(username: String) {
        val client = Browser(null)
        val fields = listOf("username" to username, "password" to passwords.getValue(username))
        val response = client.postForm("/login", emptyMap(), fields)
        val location = response.headers().firstValue("Location").orElse("")
        check(response.statusCode() == 302 && !location.contains("/login")) { "could not sign in as $username" }
        browser = client
        signedInAs = username to passwords.getValue(username)
    }

    /**
     * A fresh client that logged in as [username] ticking Remember me and was then closed: it holds that account's
     * remember-me cookie and no session, like a browser reopened later.
     */
    fun rememberedBrowser(username: String): Browser {
        val client = Browser(null)
        val fields = listOf("username" to username, "password" to passwords.getValue(username), "remember-me" to "on")
        val response = client.postForm("/login", emptyMap(), fields)
        check(response.statusCode() == 302) { "could not log in as $username" }
        client.endSession()
        return client
    }

    fun userId(username: String): Long =
        jdbc()
            .sql("SELECT id FROM users WHERE username = ?")
            .param(username)
            .query(Long::class.java)
            .single()

    /** The id of the account the test client is signed in as; the owner when it is not signed in. */
    fun currentUserId(): Long = userId(signedInAs?.first ?: World.OWNER)

    /** The signed-in user's source titled [title], created (with a URL derived from the title) when they have none. */
    fun ownedSource(title: String): Source {
        val sources = bean(SourceRepository::class.java)
        val me = currentUserId()
        return sources.findAllOf(me).firstOrNull { it.title == title }
            ?: sources.insert(me, "http://$title.test/feed", title)
    }

    /** The owner of item [id]. */
    fun ownerOfItem(id: Long): Long =
        jdbc()
            .sql("SELECT s.owner_id FROM items i JOIN sources s ON s.id = i.source_id WHERE i.id = ?")
            .param(id)
            .query(Long::class.java)
            .single()

    /**
     * The id of the item titled [title]. Titles are not unique across users (two users can follow one feed), so the
     * signed-in user's item wins; otherwise any user's, so a user can deliberately target another's item.
     */
    fun itemId(title: String): Long =
        namedId(
            "SELECT i.id FROM items i JOIN sources s ON s.id = i.source_id WHERE i.title = ? " +
                "ORDER BY s.owner_id = ? DESC, i.id LIMIT 1",
            title,
            "item",
        )

    /** The id of the source titled [title], preferring the signed-in user's like [itemId]. */
    fun sourceId(title: String): Long =
        namedId("SELECT id FROM sources WHERE title = ? ORDER BY owner_id = ? DESC, id LIMIT 1", title, "source")

    /** The id of the group named [name], preferring the signed-in user's like [itemId]. */
    fun groupId(name: String): Long {
        val sql = "SELECT id FROM groups WHERE name = ? ORDER BY owner_id = ? DESC, id LIMIT 1"
        return namedId(sql, name, "group")
    }

    private fun namedId(
        sql: String,
        name: String,
        what: String,
    ): Long =
        jdbc()
            .sql(sql)
            .params(name, currentUserId())
            .query(Long::class.java)
            .optional()
            .orElseThrow { IllegalStateException("no $what named '$name'") }

    fun start() {
        check(context == null) { "application already running" }
        if (legacyUpgrade) upgradeLegacyDatabase()
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
                    // The feed server is on localhost, which the image proxy refuses unless it is trusted.
                    "--starve.images.trusted-hosts=localhost",
                    "--starve.youtube.oembed-url=${oembedUrl()}",
                    "--starve.soundcloud.oembed-url=${soundCloudOembedUrl()}",
                    *extraArgs.toTypedArray(),
                )
    }

    fun isRunning(): Boolean = context != null

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

    /** Where the running application answers, e.g. "http://localhost:51234", for requests made outside a browser. */
    fun baseUrl(): String = "http://localhost:" + running().environment.getProperty("local.server.port")

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

    private companion object {
        /** The lowest BCrypt cost: hashing speed does not matter to the harness. */
        const val BCRYPT_COST = 4

        /** The password of every account [seedUser] creates. */
        const val MEMBER_PASSWORD = "correct-horse-1"
    }

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
