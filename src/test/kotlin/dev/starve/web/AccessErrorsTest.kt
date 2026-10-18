package dev.starve.web

import dev.starve.domain.AccountRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AccessErrorsTest {
    @Test
    fun `each rejection names the field it is about`() {
        assertEquals("username", invalidFieldOf(AccountRules.USERNAME_ERROR, "password"))
        assertEquals("username", invalidFieldOf(AccountRules.USERNAME_TAKEN, "password"))
        assertEquals("password", invalidFieldOf(AccountRules.PASSWORD_SHORT, "password"))
        assertEquals("new-password", invalidFieldOf(AccountRules.PASSWORD_LONG, "new-password"))
        assertEquals("code", invalidFieldOf(AccountRules.INVALID_SETUP_CODE, "password"))
        assertEquals("current-password", invalidFieldOf(AccountRules.CURRENT_PASSWORD_WRONG, "new-password"))
    }

    @Test
    fun `too many attempts is about no one field`() {
        assertEquals(null, invalidFieldOf(AccountRules.TOO_MANY_ATTEMPTS, "password"))
    }
}
