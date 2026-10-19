package dev.starve.web

import dev.starve.application.OWNER
import dev.starve.application.ResumeService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class PositionControllerTest {
    private val saved = mutableListOf<Long?>()
    private val controller =
        PositionController(ResumeService { _, id, seconds -> (id == 1L).also { if (it) saved += seconds } })

    private fun status(action: () -> Unit) = assertThrows(ResponseStatusException::class.java) { action() }.statusCode

    @Test
    fun `a position is stored and answered with no content`() {
        assertEquals(HttpStatus.NO_CONTENT, controller.stoppedAt(OWNER, 1, 2472, 7200).statusCode)
        assertEquals(HttpStatus.NO_CONTENT, controller.stoppedAt(OWNER, 1, 0, 0).statusCode)
        assertEquals(listOf(2472L, null), saved)
    }

    @Test
    fun `a negative time is a bad request and changes nothing`() {
        assertEquals(HttpStatus.BAD_REQUEST, status { controller.stoppedAt(OWNER, 1, -1, null) })
        assertEquals(HttpStatus.BAD_REQUEST, status { controller.stoppedAt(OWNER, 1, 10, -1) })
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `an item that is not the user's is not found`() =
        assertEquals(HttpStatus.NOT_FOUND, status { controller.stoppedAt(OWNER, 2, 60, null) })
}
