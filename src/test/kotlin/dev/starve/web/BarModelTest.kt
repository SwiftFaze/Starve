package dev.starve.web

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class BarModelTest {
    private val advice = BarModel()

    @Test
    fun `an admin gets the admin flag that shows the Settings link`() {
        val request = MockHttpServletRequest().apply { addUserRole("ADMIN") }

        assertTrue(advice.admin(request))
    }

    @Test
    fun `a regular user does not`() {
        val request = MockHttpServletRequest().apply { addUserRole("USER") }

        assertFalse(advice.admin(request))
    }

    @Test
    fun `nobody signed in does not`() {
        assertFalse(advice.admin(MockHttpServletRequest()))
    }
}
