package dev.starve.acceptance

import dev.starve.StarveApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.core.simple.JdbcClient
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

/** Starts, stops and queries the real application against one temporary SQLite file. */
class ApplicationHarness(
    private val oembedUrl: () -> String,
    private val soundCloudOembedUrl: () -> String,
) {
    private var dbFile: Path = newDbFile()
    private var context: ConfigurableApplicationContext? = null
    private var extraArgs: List<String> = emptyList()

    /** Holds the post-import polls of the running application until a step releases them. */
    var importRunner = ManualRunner()
        private set

    private val client = HttpClient.newHttpClient()

    val dbPath: Path get() = dbFile

    fun freshDatabase() {
        stop()
        extraArgs = emptyList()
        dbFile = newDbFile()
    }

    fun start() {
        check(context == null) { "application already running" }
        val runner = ManualRunner().also { importRunner = it }
        context =
            SpringApplicationBuilder(StarveApplication::class.java)
                .initializers(useRunner(runner))
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
    }

    /** Restarts on the same database with extra `--property=value` arguments, kept until the next fresh database. */
    fun restartWith(vararg args: String) {
        extraArgs = args.toList()
        restart()
    }

    fun restart() {
        stop()
        start()
    }

    fun get(
        path: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> {
        val port = running().environment.getProperty("local.server.port")
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET()
        headers.forEach { (k, v) -> builder.header(k, v) }
        val request = builder.build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun postForm(
        path: String,
        vararg fields: Pair<String, String>,
    ): HttpResponse<String> = postForm(path, emptyMap(), *fields)

    fun postForm(
        path: String,
        headers: Map<String, String>,
        vararg fields: Pair<String, String>,
    ): HttpResponse<String> {
        val port = running().environment.getProperty("local.server.port")
        val body = fields.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val builder =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port$path"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
        headers.forEach { (k, v) -> builder.header(k, v) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    /** Uploads [content] as the multipart file field [field] named [filename]. */
    fun postFile(
        path: String,
        field: String,
        filename: String,
        content: ByteArray,
    ): HttpResponse<String> {
        val port = running().environment.getProperty("local.server.port")
        val boundary = "starve-boundary-${System.nanoTime()}"
        val head = "--$boundary\r\nContent-Disposition: form-data; name=\"$field\"; filename=\"$filename\"\r\n"
        val part = "Content-Type: application/octet-stream\r\n\r\n"
        val body = (head + part).toByteArray() + content + "\r\n--$boundary--\r\n".toByteArray()
        val request =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port$path"))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

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

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    private fun running() = requireNotNull(context) { "application not running" }

    private fun newDbFile(): Path = Files.createTempDirectory("starve-test").resolve("starve.db")
}
