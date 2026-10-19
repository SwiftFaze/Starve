package dev.starve.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResumeServiceTest {
    private val saved = mutableListOf<Triple<Long, Long, Long?>>()
    private val service =
        ResumeService { owner, id, seconds ->
            saved += Triple(owner, id, seconds)
            id == 1L
        }

    @Test
    fun `a position part way is kept, and one barely started or at the end is forgotten`() {
        assertTrue(service.stoppedAt(OWNER, 1, 2472, 7200))
        service.stoppedAt(OWNER, 1, 3, null)
        service.stoppedAt(OWNER, 1, 7195, 7200)

        assertEquals(listOf(Triple(OWNER, 1L, 2472L), Triple(OWNER, 1L, null), Triple(OWNER, 1L, null)), saved)
    }

    @Test
    fun `an item that is not the owner's is refused`() = assertFalse(service.stoppedAt(OWNER, 2, 60, null))
}
