package dev.starve.acceptance

import dev.starve.application.BackgroundRunner
import java.util.concurrent.ConcurrentLinkedQueue

/** A background runner that holds tasks until a step releases them, so scenarios decide when import polls run. */
class ManualRunner : BackgroundRunner {
    private val pending = ConcurrentLinkedQueue<Runnable>()

    override fun run(task: Runnable) {
        pending += task
    }

    /** Runs every held task on the calling thread. */
    fun runPending() {
        while (true) (pending.poll() ?: return).run()
    }
}
