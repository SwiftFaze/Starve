package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom

class SetupCodeRulesTest {
    private val shape = Regex("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}-[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}")

    @Test
    fun `generated codes are two groups of four from the unambiguous alphabet`() {
        repeat(50) {
            val code = SetupCodeRules.generate(SecureRandom())
            assertTrue(shape.matches(code), code)
        }
    }

    @Test
    fun `a code matches itself in any case, with or without its hyphen`() {
        assertTrue(SetupCodeRules.matches("AB3D-EFGH", "AB3D-EFGH"))
        assertTrue(SetupCodeRules.matches("AB3D-EFGH", "ab3d-efgh"))
        assertTrue(SetupCodeRules.matches("AB3D-EFGH", "ab3defgh"))
        assertTrue(SetupCodeRules.matches("AB3D-EFGH", " AB3DEFGH "))
    }

    @Test
    fun `a different or empty code does not match`() {
        assertFalse(SetupCodeRules.matches("AB3D-EFGH", "AB3D-EFGJ"))
        assertFalse(SetupCodeRules.matches("AB3D-EFGH", "AB3D-EFG"))
        assertFalse(SetupCodeRules.matches("AB3D-EFGH", ""))
    }
}
