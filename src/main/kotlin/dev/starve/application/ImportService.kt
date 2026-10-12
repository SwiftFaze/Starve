package dev.starve.application

import dev.starve.domain.GroupRules
import dev.starve.domain.ImportEntry
import dev.starve.domain.ParsedImport
import dev.starve.domain.Source
import dev.starve.domain.TakeoutCsv

/** What an import did: new subscriptions, ones already held, and the reason for each unusable row. */
data class ImportSummary(
    val added: Int,
    val alreadySubscribed: Int,
    val failures: List<String>,
) {
    val failed: Int get() = failures.size
}

sealed interface ImportOutcome {
    data class Done(
        val summary: ImportSummary,
    ) : ImportOutcome

    data object Unrecognised : ImportOutcome
}

/** Imports many subscriptions from a YouTube Takeout CSV or an OPML file, without fetching during the call. */
class ImportService(
    private val sources: SourceRepository,
    private val groups: GroupRepository,
    private val opml: OpmlReader,
    private val background: BackgroundPoller,
    private val youtubeFeedUrlTemplate: String,
) {
    /** Detects the type of [content], stores its subscriptions and polls the new ones in the background. */
    fun importFile(content: ByteArray): ImportOutcome {
        val text = String(content, Charsets.UTF_8).removePrefix("\uFEFF")
        val parsed = parse(text) ?: return ImportOutcome.Unrecognised
        val session = Run()
        parsed.entries.forEach { session.apply(it) }
        if (session.addedIds.isNotEmpty()) background.poll(session.addedIds.toList())
        return ImportOutcome.Done(ImportSummary(session.addedIds.size, session.alreadySubscribed, parsed.failures))
    }

    private fun parse(text: String): ParsedImport? =
        if (text.trimStart().startsWith("<")) opml.read(text) else TakeoutCsv.parse(text, youtubeFeedUrlTemplate)

    private inner class Run {
        private val known: MutableMap<String, Source> = sources.findAll().associateBy { it.url }.toMutableMap()
        private val groupIds = mutableMapOf<String, Long>()
        val addedIds = mutableListOf<Long>()
        var alreadySubscribed = 0

        fun apply(entry: ImportEntry) {
            val existing = known[entry.url]
            val source = existing ?: sources.insert(entry.url, entry.title).also { addedIds += it.id }
            if (existing != null) alreadySubscribed++
            known[entry.url] = if (source.groupId == null) grouped(source, entry.folder) else source
        }

        private fun grouped(
            source: Source,
            folder: String?,
        ): Source {
            val groupId = folder?.let { groupFor(it) } ?: return source
            sources.assignGroup(source.id, groupId)
            return source.copy(groupId = groupId)
        }

        private fun groupFor(folder: String): Long? {
            val name = GroupRules.truncatedName(folder) ?: return null
            return groupIds.getOrPut(name.lowercase()) { (groups.findByName(name) ?: groups.insert(name)).id }
        }
    }
}
