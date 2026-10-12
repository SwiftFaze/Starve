package dev.starve.domain

/** A named collection of sources; a source belongs to at most one. */
data class Group(
    val id: Long,
    val name: String,
)

/** A group with the number of unread items across its sources. */
data class GroupSummary(
    val group: Group,
    val unread: Int,
)

/** The outcome of checking a proposed group name. */
sealed interface NameCheck {
    data class Valid(
        val name: String,
    ) : NameCheck

    data object Blank : NameCheck

    data object TooLong : NameCheck
}

object GroupRules {
    const val MAX_NAME_LENGTH = 50

    /** Trims [raw] and checks it is 1 to [MAX_NAME_LENGTH] characters. */
    fun checkName(raw: String): NameCheck {
        val name = raw.trim()
        return when {
            name.isEmpty() -> NameCheck.Blank
            name.length > MAX_NAME_LENGTH -> NameCheck.TooLong
            else -> NameCheck.Valid(name)
        }
    }

    /** [raw] trimmed and cut to [MAX_NAME_LENGTH] characters; null when nothing is left. */
    fun truncatedName(raw: String): String? =
        raw
            .trim()
            .take(MAX_NAME_LENGTH)
            .trim()
            .ifEmpty { null }
}
