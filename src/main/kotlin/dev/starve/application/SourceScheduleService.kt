package dev.starve.application

import dev.starve.domain.PollInterval

/** How often each of a user's sources is fetched, and fetching one now. */
class SourceScheduleService(
    private val schedule: SourceScheduleRepository,
    private val sources: SourceRepository,
    private val background: BackgroundPoller,
) {
    /** Sets [owner]'s source [id] to be fetched every [interval], or on the default when null; false if not theirs. */
    fun setInterval(
        owner: Long,
        id: Long,
        interval: PollInterval?,
    ): Boolean = schedule.setInterval(owner, id, interval)

    /**
     * Starts fetching [owner]'s source [id] in the background and returns at once; a fetch of it already queued or
     * running is not queued again. False when [owner] has no such source.
     */
    fun fetchNow(
        owner: Long,
        id: Long,
    ): Boolean {
        if (sources.findAllOf(owner).none { it.id == id }) return false
        background.poll(listOf(id))
        return true
    }

    /** True while a background fetch of source [id] is queued or running. */
    fun isFetching(id: Long): Boolean = background.isFetching(id)
}
