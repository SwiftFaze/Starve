package dev.starve.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserAdminServiceTest {
    private val users = FakeUserAdmin("rob", "amy", "Bo")
    private val service = UserAdminService(users, { "hashed:$it" })
    private val adminId = 1L

    @Test
    fun `the list is every user in the order the repository gives`() {
        assertEquals(listOf("rob", "amy", "Bo"), service.list().map { it.username })
    }

    @Test
    fun `looking up another user names them`() {
        assertEquals(UserAction.Done("amy"), service.lookup(adminId, 2))
    }

    @Test
    fun `looking up oneself or nobody is refused`() {
        assertEquals(UserAction.Self, service.lookup(adminId, adminId))
        assertEquals(UserAction.Missing, service.lookup(adminId, 99))
    }

    @Test
    fun `a reset stores the hash of a new temporary password and returns it`() {
        val reset = (service.resetPassword(adminId, 2) as UserAction.Done).value
        val temporary = reset.temporaryPassword
        assertEquals("amy", reset.username)

        assertTrue(Regex("[A-Z2-9]{4}(-[A-Z2-9]{4}){3}").matches(temporary), temporary)
        assertEquals("hashed:$temporary", users.findById(2)!!.passwordHash)
        assertEquals("hashed:pw-rob", users.findById(1)!!.passwordHash)
        assertEquals("hashed:pw-Bo", users.findById(3)!!.passwordHash)
    }

    @Test
    fun `every reset makes a new password`() {
        val first = (service.resetPassword(adminId, 2) as UserAction.Done).value.temporaryPassword
        val second = (service.resetPassword(adminId, 2) as UserAction.Done).value.temporaryPassword

        assertTrue(first != second)
    }

    @Test
    fun `a reset of oneself or nobody changes nothing`() {
        assertEquals(UserAction.Self, service.resetPassword(adminId, adminId))
        assertEquals(UserAction.Missing, service.resetPassword(adminId, 99))

        assertEquals(listOf("hashed:pw-rob", "hashed:pw-amy", "hashed:pw-Bo"), users.rows.map { it.passwordHash })
    }

    @Test
    fun `a delete removes that user only and names them`() {
        assertEquals(UserAction.Done("amy"), service.delete(adminId, 2))

        assertEquals(listOf("rob", "Bo"), users.rows.map { it.username })
    }

    @Test
    fun `a delete of oneself or nobody removes nothing`() {
        assertEquals(UserAction.Self, service.delete(adminId, adminId))
        assertEquals(UserAction.Missing, service.delete(adminId, 99))

        assertEquals(3, users.rows.size)
    }
}
