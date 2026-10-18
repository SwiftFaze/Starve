package dev.starve.application

import java.util.concurrent.ConcurrentHashMap

/** Polls chosen sources off the calling thread, never queueing a source whose fetch is already queued or running. */
class BackgroundPoller(
    private val sources: SourceRepository,
    private val poller: PollService,
    private val runner: BackgroundRunner,
) {
    private val inFlight: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /** Returns at once; the sources with these [ids] are fetched on the runner's thread, those in flight skipped. */
    fun poll(ids: List<Long>) {
        val fresh = ids.filter { inFlight.add(it) }
        if (fresh.isNotEmpty()) runner.run { fresh.forEach(::pollOne) }
    }

    /** True from the moment source [id] is queued until its fetch is over. */
    fun isFetching(id: Long): Boolean = id in inFlight

    private fun pollOne(id: Long) {
        try {
            sources.findById(id)?.let { poller.pollOne(it) }
        } finally {
            inFlight.remove(id)
        }
    }
}
