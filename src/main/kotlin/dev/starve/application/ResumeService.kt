package dev.starve.application

import dev.starve.domain.ResumeRules

/** Persistence of where each item's playback stopped, per user like the items themselves. */
fun interface PositionRepository {
    /** Stores [seconds] as [owner]'s position in item [id], or forgets it when null; false when it is not theirs. */
    fun save(
        owner: Long,
        id: Long,
        seconds: Long?,
    ): Boolean
}

/** Remembers where playback stopped, so an item resumes there on any device, and forgets it once played through. */
class ResumeService(
    private val positions: PositionRepository,
) {
    /** Records that [owner]'s item [id] stopped [at] seconds into [duration] (when known); false when not theirs. */
    fun stoppedAt(
        owner: Long,
        id: Long,
        at: Long,
        duration: Long?,
    ): Boolean = positions.save(owner, id, ResumeRules.kept(at, duration))
}
