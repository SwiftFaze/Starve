package dev.starve.domain

import java.time.Instant

/**
 * How an item came to be read at [at]: [opened] when the user really opened it (its link, the reader, the detail
 * view or the player), false when it was only marked read, which counts as a skip.
 */
data class ReadMark(
    val at: Instant,
    val opened: Boolean,
) {
    companion object {
        fun opened(at: Instant) = ReadMark(at, true)

        fun skipped(at: Instant) = ReadMark(at, false)
    }
}
