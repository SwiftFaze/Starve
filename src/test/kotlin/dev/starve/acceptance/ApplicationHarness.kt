package dev.starve.acceptance

import dev.starve.StarveApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.core.simple.JdbcClient
import java.net.URI
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
                .run("--server.port=0", "--starve.db.path=$dbFile")
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

    fun jdbc(): JdbcClient = JdbcClient.create(running().getBean(DataSource::class.java))

    fun cleanUp() {
        stop()
        Files.deleteIfExists(dbFile)
    }

    private fun running() = requireNotNull(context) { "application not running" }

    private fun newDbFile(): Path = Files.createTempDirectory("starve-test").resolve("starve.db")
}
