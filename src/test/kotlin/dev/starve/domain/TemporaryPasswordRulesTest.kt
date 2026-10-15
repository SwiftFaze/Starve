package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom

class TemporaryPasswordRulesTest {
    private val group = "[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}"
    private val shape = Regex("$group-$group-$group-$group")

    @Test
    fun `a temporary password is four groups of four from the unambiguous alphabet`() {
        repeat(50) {
            val password = TemporaryPasswordRules.generate(SecureRandom())
            assertTrue(shape.matches(password), password)
            assertEquals(19, password.length)
        }
    }

    @Test
    fun `a temporary password satisfies the normal password rules`() {
        assertNull(AccountRules.passwordError(TemporaryPasswordRules.generate(SecureRandom())))
    }

    @Test
    fun `every call makes a new password`() {
        val random = SecureRandom()
        assertNotEquals(TemporaryPasswordRules.generate(random), TemporaryPasswordRules.generate(random))
    }
}
