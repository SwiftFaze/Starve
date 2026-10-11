package dev.starve.application

/** Polls chosen sources off the calling thread. */
class BackgroundPoller(
    private val sources: SourceRepository,
    private val poller: PollService,
    private val runner: BackgroundRunner,
) {
    /** Returns at once; the sources with these [ids] are fetched on the runner's thread. */
    fun poll(ids: List<Long>) = runner.run { ids.forEach { id -> sources.findById(id)?.let { poller.pollOne(it) } } }
}
