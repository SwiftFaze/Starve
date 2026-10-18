package dev.starve.web

import dev.starve.application.BackgroundPoller
import dev.starve.application.FeedFetcher
import dev.starve.application.FeedIngestor
import dev.starve.application.FetchResult
import dev.starve.application.GroupService
import dev.starve.application.MemoryGroups
import dev.starve.application.MemoryItems
import dev.starve.application.MemorySources
import dev.starve.application.MemoryTags
import dev.starve.application.OTHER_OWNER
import dev.starve.application.OWNER
import dev.starve.application.PollService
import dev.starve.application.SourceHealthService
import dev.starve.application.SourceScheduleService
import dev.starve.application.TagService
import dev.starve.application.quietFavicons
import dev.starve.domain.Group
import dev.starve.domain.Tag
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.ui.ConcurrentModel
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration

class TagControllerTest {
    private val sources = MemorySources()
    private val groups = MemoryGroups(mutableListOf(Group(1, "Tech")))
    private val tags = MemoryTags()
    private val fetcher =
        object : FeedFetcher {
            override fun fetch(
                url: String,
                validators: Validators,
            ) = FetchResult.NotModified()
        }
    private val ingestor = FeedIngestor(sources, MemoryItems(), Clock.systemUTC(), quietFavicons(sources))
    private val schedule =
        SourceScheduleService(
            { _, _, _ -> true },
            sources,
            BackgroundPoller(sources, PollService(sources, fetcher, ingestor)) { },
        )
    private val roster =
        SourceRoster(
            SourceHealthService(sources, { _, _, _ -> emptyMap() }, Clock.systemUTC()),
            GroupService(groups, sources, tags),
            TagService(tags, groups),
            schedule,
            Clock.systemUTC(),
            Duration.ofMinutes(30),
        )
    private val controller = TagController(TagService(tags, groups), roster)

    private fun statusOf(action: () -> Unit) = assertThrows(ResponseStatusException::class.java) { action() }.statusCode

    @Test
    fun `a saved tag redirects to Sources, which lists it`() {
        val view = controller.create(OWNER, TagEntry("AI", "AI, LLM", body = "1", group = "1"), ConcurrentModel())

        assertEquals("redirect:/sources", view)
        assertEquals(listOf(Tag(1, "AI", listOf("AI", "LLM"), true, 1)), tags.findAll(OWNER))
        val model = ConcurrentModel()
        controller.confirmDelete(OWNER, 1, model)
        assertEquals(tags.findAll(OWNER), model.getAttribute("tags"))
    }

    @Test
    fun `a rejected new tag redraws Sources with why and keeps what was typed`() {
        val model = ConcurrentModel()

        val view = controller.create(OWNER, TagEntry("AI", "C++", body = "1", group = "1"), model)

        assertEquals("sources", view)
        assertEquals(
            "\"C++\" is not a valid term: use letters, digits and spaces, with * only at the end",
            model.getAttribute("message"),
        )
        assertEquals(true, model.getAttribute("error"))
        assertEquals("AI", model.getAttribute("tagName"))
        assertEquals("C++", model.getAttribute("tagTerms"))
        assertEquals(true, model.getAttribute("tagBody"))
        assertEquals(1L, model.getAttribute("tagGroup"))
    }

    @Test
    fun `each rejection has its message`() {
        controller.create(OWNER, TagEntry("AI", "AI"), ConcurrentModel())
        val messages =
            listOf(
                TagEntry("", "AI") to "tag name is required",
                TagEntry("x".repeat(51), "AI") to "at most 50 characters",
                TagEntry("ai", "AI") to "a tag with that name already exists",
                TagEntry("B", " , ") to "at least one term is required",
                TagEntry("B", (1..21).joinToString { "t$it" }) to "at most 20 terms",
            )

        messages.forEach { (entry, message) ->
            val model = ConcurrentModel()
            controller.create(OWNER, entry, model)
            assertEquals(message, model.getAttribute("message"))
        }
    }

    @Test
    fun `an edit replaces the tag, and a rejected one leaves the new tag form empty`() {
        controller.create(OWNER, TagEntry("AI", "AI"), ConcurrentModel())
        val model = ConcurrentModel()

        assertEquals("redirect:/sources", controller.update(OWNER, 1, TagEntry("AI", "LLM"), ConcurrentModel()))
        assertEquals("sources", controller.update(OWNER, 1, TagEntry("AI", ""), model))

        assertEquals(Tag(1, "AI", listOf("LLM")), tags.findById(OWNER, 1))
        assertNull(model.getAttribute("tagName"))
    }

    @Test
    fun `another user's tag, an unknown group and a malformed group are not found`() {
        controller.create(OWNER, TagEntry("AI", "AI"), ConcurrentModel())

        val model = ConcurrentModel()
        assertEquals(HttpStatus.NOT_FOUND, statusOf { controller.update(OTHER_OWNER, 1, TagEntry("X", "x"), model) })
        listOf("9", "x").forEach { group ->
            val entry = TagEntry("B", "b", group = group)
            assertEquals(HttpStatus.NOT_FOUND, statusOf { controller.create(OWNER, entry, model) })
        }
        assertEquals(HttpStatus.NOT_FOUND, statusOf { controller.delete(OTHER_OWNER, 1) })
    }

    @Test
    fun `a blank group is any group, and delete asks first then deletes`() {
        controller.create(OWNER, TagEntry("AI", "AI", group = " "), ConcurrentModel())
        val model = ConcurrentModel()

        assertEquals("sources", controller.confirmDelete(OWNER, 1, model))
        assertEquals(1L, model.getAttribute("deletingTag"))
        assertNull(tags.findById(OWNER, 1)?.groupId)
        assertEquals("redirect:/sources", controller.delete(OWNER, 1))
        assertFalse(tags.delete(OWNER, 1))
    }
}
