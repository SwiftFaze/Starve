package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccountRulesTest {
    @Test
    fun `usernames of 3 to 32 letters, digits, dots, underscores and hyphens are allowed`() {
        listOf("rob", "R.o_b-42", "a".repeat(32)).forEach { assertNull(AccountRules.usernameError(it), it) }
    }

    @Test
    fun `short, long, blank and other characters are rejected`() {
        listOf("", "ro", "a".repeat(33), "rob woolley", "rob!", "röb").forEach {
            assertEquals(AccountRules.USERNAME_ERROR, AccountRules.usernameError(it), it)
        }
    }

    @Test
    fun `passwords need at least ten characters`() {
        assertEquals(AccountRules.PASSWORD_SHORT, AccountRules.passwordError("short-pw1"))
        assertNull(AccountRules.passwordError("1234567890"))
    }

    @Test
    fun `passwords may be at most 72 UTF-8 bytes`() {
        assertNull(AccountRules.passwordError("a".repeat(72)))
        assertEquals(AccountRules.PASSWORD_LONG, AccountRules.passwordError("a".repeat(73)))
    }

    @Test
    fun `length counts characters, not bytes, for the minimum`() {
        // Ten characters of 2 bytes each: 20 bytes, so the minimum passes and the maximum does not apply.
        assertNull(AccountRules.passwordError("é".repeat(10)))
        // 37 characters of 2 bytes each: 74 bytes, over the limit.
        assertEquals(AccountRules.PASSWORD_LONG, AccountRules.passwordError("é".repeat(37)))
    }
}
