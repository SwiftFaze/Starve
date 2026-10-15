package dev.starve.infra

import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.migration.Context
import org.flywaydb.core.api.migration.JavaMigration
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import java.sql.ResultSet

/**
 * V15: runs every stored post body through [BodySanitizer] again, once, so bodies stored before it learned to drop
 * heading permalink marks lose them, and drops a leading `<h1>` that only repeats the item's title (the reader
 * already shows it). Cleaning twice changes nothing else, and only bodies that change are rewritten. Java rather
 * than SQL because the cleaning is jsoup.
 */
@Component
class CleanItemBodiesMigration : JavaMigration {
    override fun getVersion(): MigrationVersion = MigrationVersion.fromVersion("15")

    override fun getDescription(): String = "clean stored post bodies again"

    override fun getChecksum(): Int? = null

    override fun canExecuteInTransaction(): Boolean = true

    override fun migrate(context: Context) {
        val changed = storedBodies(context).mapNotNull { it.cleaned()?.let { body -> it.id to body } }
        context.connection.prepareStatement("UPDATE items SET body = ? WHERE id = ?").use { update ->
            changed.forEach { (id, body) ->
                update.setString(1, body)
                update.setLong(2, id)
                update.addBatch()
            }
            update.executeBatch()
        }
    }

    private class Stored(
        val id: Long,
        val title: String,
        val link: String?,
        val body: String,
    ) {
        /** The new body when cleaning changes it; null when it does not, or when nothing would be left. */
        fun cleaned(): String? =
            BodySanitizer
                .sanitize(body, link)
                ?.let(::withoutRepeatedTitle)
                ?.takeIf { it.isNotBlank() && it != body }

        // Compared after cleaning, so a permalink mark in the heading no longer hides that it is the title.
        private fun withoutRepeatedTitle(clean: String): String {
            val fragment = Jsoup.parseBodyFragment(clean)
            fragment.outputSettings().prettyPrint(false)
            val first = fragment.body().select("h1, p").firstOrNull()
            if (first?.tagName() != "h1" || !first.text().trim().equals(title.trim(), ignoreCase = true)) return clean
            first.remove()
            return fragment.body().html().trim()
        }
    }

    private fun storedBodies(context: Context): List<Stored> =
        context.connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id, title, link, body FROM items WHERE body IS NOT NULL").use { it.rows() }
        }

    private fun ResultSet.rows(): List<Stored> =
        generateSequence {
            if (next()) Stored(getLong("id"), getString("title"), getString("link"), getString("body")) else null
        }.toList()
}
