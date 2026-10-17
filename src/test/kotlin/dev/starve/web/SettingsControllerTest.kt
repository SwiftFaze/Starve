package dev.starve.web

import dev.starve.application.AppMetadata
import dev.starve.application.FakeUserAdmin
import dev.starve.application.SignupSettings
import dev.starve.application.UserAdminService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.ui.ConcurrentModel

class SettingsControllerTest {
    private val noMetadata =
        object : AppMetadata {
            override fun find(key: String): String? = null

            override fun putIfAbsent(
                key: String,
                value: String,
            ) = true

            override fun put(
                key: String,
                value: String,
            ) = Unit
        }
    private val controller =
        SettingsController(SignupSettings(noMetadata), UserAdminService(FakeUserAdmin("rob", "amy"), { "hashed:$it" }))

    @Test
    fun `the page names the user whose reset it is confirming`() {
        val model = ConcurrentModel()

        assertEquals("settings", controller.page(1, model, reset = 2))
        assertEquals(2L, model.getAttribute("resetting"))
    }

    @Test
    fun `the plain page confirms nothing`() {
        val model = ConcurrentModel()

        controller.page(1, model)

        assertNull(model.getAttribute("resetting"))
    }
}
