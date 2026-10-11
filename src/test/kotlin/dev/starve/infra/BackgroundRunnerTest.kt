package dev.starve.infra

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackgroundRunnerTest {
    @Test
    fun `tasks run on another daemon thread while the caller carries on`() {
        val done = CountDownLatch(1)
        val ran = arrayOfNulls<Thread>(1)

        ImportConfig("").backgroundRunner().run {
            ran[0] = Thread.currentThread()
            done.countDown()
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertNotEquals(Thread.currentThread(), ran[0])
        assertTrue(ran[0]!!.isDaemon)
    }
}
