package dev.starve.web

import dev.starve.application.BackgroundPoller
import dev.starve.application.FeedFetcher
import dev.starve.application.FeedIngestor
import dev.starve.application.FetchResult
import dev.starve.application.GroupService
import dev.starve.application.MemoryGroups
import dev.starve.application.MemoryItems
import dev.starve.application.MemorySources
import dev.starve.application.OTHER_OWNER
import dev.starve.application.OWNER
import dev.starve.application.PollService
import dev.starve.application.SourceHealthService
import dev.starve.application.SourceScheduleService
import dev.starve.application.quietFavicons
import dev.starve.domain.PollInterval
import dev.starve.domain.Source
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.ui.ConcurrentModel
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration

class SourceScheduleControllerTest {
    private val sources =
        MemorySources(
            mutableListOf(
                Source(4, "http://a.test/feed", "Title", Validators(), null, null, pollInterval = PollInterval.HOURLY),
                Source(5, "http://b.test/feed", "Theirs", Validators(), null, null),
            ),
            mutableMapOf(5L to OTHER_OWNER),
        )
    private val saved = mutableListOf<Pair<Long, PollInterval?>>()
    private val queued = mutableListOf<Runnable>()
    private val fetcher =
        object : FeedFetcher {
            override fun fetch(
                url: String,
                validators: Validators,
            ) = FetchResult.NotModified
        }
    private val ingestor = FeedIngestor(sources, MemoryItems(), Clock.systemUTC(), quietFavicons(sources))
    private val schedule =
        SourceScheduleService(
            { owner, id, interval -> (owner == OWNER && id == 4L).also { if (it) saved += id to interval } },
            sources,
            BackgroundPoller(sources, PollService(sources, fetcher, ingestor)) { queued += it },
        )
    private val roster =
        SourceRoster(
            SourceHealthService(sources, { _, _, _ -> emptyMap() }, Clock.systemUTC()),
            GroupService(MemoryGroups(), sources),
            schedule,
            Clock.systemUTC(),
            Duration.ofMinutes(30),
        )
    private val controller = SourceScheduleController(schedule, roster)

    private fun statusOf(action: () -> Unit) = assertThrows(ResponseStatusException::class.java) { action() }.statusCode

    @Test
    fun `an interval is set by name, blank goes back to the default, and the roster is shown again`() {
        assertEquals("redirect:/sources", controller.setInterval(OWNER, 4, "DAILY"))
        assertEquals("redirect:/sources", controller.setInterval(OWNER, 4, " "))
        assertEquals("redirect:/sources", controller.setInterval(OWNER, 4, null))

        assertEquals(listOf(4L to PollInterval.DAILY, 4L to null, 4L to null), saved)
    }

    @Test
    fun `an unknown interval is a bad request and another user's source is not found`() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf { controller.setInterval(OWNER, 4, "WEEKLY") })
        assertEquals(HttpStatus.NOT_FOUND, statusOf { controller.setInterval(OWNER, 5, "DAILY") })
        assertEquals(emptyList<Pair<Long, PollInterval?>>(), saved)
    }

    @Test
    fun `fetch now answers htmx with the row in its fetching state and a plain form with the roster`() {
        val model = ConcurrentModel()

        assertEquals("sources :: source-row", controller.fetchNow(OWNER, 4, "true", model))
        val row = (model.getAttribute("sources") as List<*>).single() as SourceView
        assertEquals(4L, row.id)
        assertEquals(true, row.fetching)
        assertEquals(PollInterval.HOURLY, row.interval)
        assertEquals("redirect:/sources", controller.fetchNow(OWNER, 4, null, ConcurrentModel()))
        assertEquals(1, queued.size)
    }

    @Test
    fun `fetch now and the row of another user's source are not found`() {
        assertEquals(HttpStatus.NOT_FOUND, statusOf { controller.fetchNow(OWNER, 5, "true", ConcurrentModel()) })
        assertEquals(HttpStatus.NOT_FOUND, statusOf { controller.row(OWNER, 5, ConcurrentModel()) })
        assertEquals(HttpStatus.NOT_FOUND, statusOf { controller.row(OWNER, 99, ConcurrentModel()) })
    }

    @Test
    fun `a row that is not fetching says so`() {
        val model = ConcurrentModel()

        assertEquals("sources :: source-row", controller.row(OWNER, 4, model))
        assertEquals(false, ((model.getAttribute("sources") as List<*>).single() as SourceView).fetching)
    }
}
