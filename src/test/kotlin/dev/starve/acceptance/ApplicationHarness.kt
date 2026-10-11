package dev.starve.acceptance

import dev.starve.StarveApplication
import org.springframework.boot.builder.SpringApplicationBuilder
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
class ApplicationHarness {
    private var dbFile: Path = newDbFile()
    private var context: ConfigurableApplicationContext? = null
    private val client = HttpClient.newHttpClient()

    val dbPath: Path get() = dbFile

    fun freshDatabase() {
        stop()
        dbFile = newDbFile()
    }

    fun start() {
        check(context == null) { "application already running" }
        context =
            SpringApplicationBuilder(StarveApplication::class.java)
                .run(
                    "--server.port=0",
                    "--starve.db.path=$dbFile",
                    "--starve.scheduling.enabled=false",
                    "--starve.fetch.timeout=PT1S",
                )
    }

    fun stop() {
        context?.close()
        context = null
    }

    fun restart() {
        stop()
        start()
    }

    fun get(path: String): HttpResponse<String> {
        val port = running().environment.getProperty("local.server.port")
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun postForm(
        path: String,
        vararg fields: Pair<String, String>,
    ): HttpResponse<String> {
        val port = running().environment.getProperty("local.server.port")
        val body = fields.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val request =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port$path"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun <T : Any> bean(type: Class<T>): T = running().getBean(type)

    fun jdbc(): JdbcClient = JdbcClient.create(running().getBean(DataSource::class.java))

    fun cleanUp() {
        stop()
        Files.deleteIfExists(dbFile)
    }

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    private fun running() = requireNotNull(context) { "application not running" }

    private fun newDbFile(): Path = Files.createTempDirectory("starve-test").resolve("starve.db")
}
