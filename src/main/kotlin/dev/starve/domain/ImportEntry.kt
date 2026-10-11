package dev.starve.domain

/** One subscription found in an uploaded file; [folder] is the OPML folder it sat in, if any. */
data class ImportEntry(
    val url: String,
    val title: String,
    val folder: String?,
)

/** What a file parser found: the subscriptions, and a reason for each row or outline it could not use. */
data class ParsedImport(
    val entries: List<ImportEntry>,
    val failures: List<String>,
)
