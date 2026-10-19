package dev.starve.domain

/** Which playback positions are worth resuming from: not one barely started, nor one in the last few seconds. */
object ResumeRules {
    /** A play stopped this close to the end counts as finished, and starts from the beginning next time. */
    const val FINISHED_WITHIN_SECONDS = 10L

    /** A play stopped before this has nothing worth resuming. */
    const val MIN_SECONDS = 5L

    /** The position to keep for a play stopped at [at] seconds into [duration] (when known); null to forget it. */
    fun kept(
        at: Long,
        duration: Long?,
    ): Long? = at.takeIf { it >= MIN_SECONDS && (duration == null || duration - it > FINISHED_WITHIN_SECONDS) }
}
