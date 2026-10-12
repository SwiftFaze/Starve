package dev.starve.domain

/** Reads the `subscriptions.csv` of a YouTube Google Takeout export. */
object TakeoutCsv {
    private const val ID_COLUMN = "Channel Id"
    private const val TITLE_COLUMN = "Channel Title"
    private val CHANNEL_ID = Regex("UC[A-Za-z0-9_-]{22}")

    /**
     * Parses [text], or returns null when its header row has no "Channel Id" column. Each valid row becomes the
     * subscription [feedUrlTemplate] with `{id}` replaced by the channel id; row numbers count the header as row 1.
     */
    fun parse(
        text: String,
        feedUrlTemplate: String,
    ): ParsedImport? {
        val records = CsvRecords.read(text.removePrefix("\uFEFF"))
        val header = records.firstOrNull()?.map { it.trim() } ?: return null
        val idColumn = header.indexOfFirst { it.equals(ID_COLUMN, ignoreCase = true) }
        return if (idColumn < 0) null else rows(records, idColumn, header.indexOfFirst(::isTitle), feedUrlTemplate)
    }

    private fun isTitle(name: String) = name.equals(TITLE_COLUMN, ignoreCase = true)

    private fun rows(
        records: List<List<String>>,
        idColumn: Int,
        titleColumn: Int,
        feedUrlTemplate: String,
    ): ParsedImport {
        val entries = mutableListOf<ImportEntry>()
        val failures = mutableListOf<String>()
        records.forEachIndexed { index, row ->
            val id = row.getOrNull(idColumn)?.trim().orEmpty()
            when {
                index == 0 || row.all { it.isBlank() } -> {
                    Unit
                }

                CHANNEL_ID.matches(id) -> {
                    val url = feedUrlTemplate.replace("{id}", id)
                    entries +=
                        ImportEntry(
                            url,
                            row
                                .getOrNull(titleColumn)
                                ?.trim()
                                .orEmpty()
                                .ifEmpty { url },
                            null,
                        )
                }

                else -> {
                    failures += "row ${index + 1}: invalid channel id"
                }
            }
        }
        return ParsedImport(entries, failures)
    }
}

/** RFC 4180 records: quoted fields may contain commas, quotes (doubled) and line breaks. */
internal class CsvRecords private constructor() {
    private val records = mutableListOf<List<String>>()
    private var row = mutableListOf<String>()
    private val field = StringBuilder()
    private var quoted = false

    private fun endField() {
        row += field.toString()
        field.setLength(0)
    }

    private fun endRecord() {
        endField()
        records += row
        row = mutableListOf()
    }

    /** Handles the character at [i]; returns how many characters were consumed. */
    private fun consume(
        text: String,
        i: Int,
    ): Int {
        val next = text.getOrNull(i + 1)
        return if (quoted) inQuotes(text[i], next) else outside(text[i], next)
    }

    private fun inQuotes(
        c: Char,
        next: Char?,
    ): Int {
        val escapedQuote = c == '"' && next == '"'
        if (c != '"' || escapedQuote) field.append(c) else quoted = false
        return if (escapedQuote) 2 else 1
    }

    private fun outside(
        c: Char,
        next: Char?,
    ): Int {
        when (c) {
            '"' -> quoted = true
            ',' -> endField()
            '\n', '\r' -> endRecord()
            else -> field.append(c)
        }
        return if (c == '\r' && next == '\n') 2 else 1
    }

    companion object {
        fun read(text: String): List<List<String>> {
            val reader = CsvRecords()
            var i = 0
            while (i < text.length) i += reader.consume(text, i)
            if (reader.field.isNotEmpty() || reader.row.isNotEmpty()) reader.endRecord()
            return reader.records
        }
    }
}
