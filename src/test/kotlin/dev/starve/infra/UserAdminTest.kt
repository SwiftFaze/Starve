package dev.starve.infra

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val LOGIN_URL = "http://localhost/login"
private const val FRIEND = "amy"
private val ROW = Regex("<li class=\"user\".*?</li>", RegexOption.DOT_MATCHES_ALL)
private const val TEMPORARY = "[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}"

/** Ids by seeding order: rob 1 (admin), sam 2, amy 3. */
class UserAdminTest : SecurityTestBase() {
    private fun signedIn(username: String) = MockHttpSession().also { login(username, session = it) }

    private fun seed() {
        withAccounts()
        users.add(FRIEND, admin = false)
    }

    private fun reset(
        id: String,
        session: MockHttpSession = signedIn(ADMIN),
    ): MvcResult = mvc.perform(post("/settings/users/$id/reset").session(session)).andReturn()

    private fun temporaryIn(result: MvcResult): String =
        requireNotNull(Regex("id=\"temporary-password\"[^>]*>([^<]+)<").find(result.response.contentAsString))
            .groupValues[1]

    // ---- access ----

    @ParameterizedTest
    @ValueSource(strings = ["/settings/users/2/delete", "/settings/users/nope/delete"])
    fun `an anonymous visitor to a user page is sent to login`(path: String) {
        seed()

        mvc.perform(get(path)).andExpect(status().isFound).andExpect(redirectedUrl(LOGIN_URL))
    }

    @ParameterizedTest
    @ValueSource(strings = ["/settings/users/2/reset", "/settings/users/2/delete", "/settings/users/3/delete"])
    fun `an anonymous post to a user action is sent to login and changes nothing`(path: String) {
        seed()

        mvc.perform(post(path)).andExpect(status().isFound).andExpect(redirectedUrl(LOGIN_URL))

        assertEquals(3, users.listAll().size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["/settings/users/1/delete", "/settings/users/3/delete", "/settings/users/9999/delete"])
    fun `a regular user is refused the confirmation page`(path: String) {
        seed()

        mvc.perform(get(path).session(signedIn(MEMBER))).andExpect(status().isForbidden)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/settings/users/1/reset", "/settings/users/3/reset", "/settings/users/9999/reset",
            "/settings/users/3/delete", "/settings/users/9999/delete",
        ],
    )
    fun `a regular user cannot reset or delete anyone`(path: String) {
        seed()
        val before = users.listAll()
        val hashes = users.hashOf(FRIEND)

        mvc.perform(post(path).session(signedIn(MEMBER))).andExpect(status().isForbidden)

        assertEquals(before, users.listAll())
        assertEquals(hashes, users.hashOf(FRIEND))
    }

    @ParameterizedTest
    @ValueSource(strings = ["reset", "delete"])
    fun `user actions need the CSRF token`(action: String) {
        seed()

        mvc
            .perform(postWithoutCsrf("/settings/users/3/$action").session(signedIn(ADMIN)))
            .andExpect(status().isForbidden)

        assertEquals(3, users.listAll().size)
    }

    // ---- the list ----

    @Test
    fun `the settings page lists every user with badges, created date and the right controls`() {
        seed()

        val page =
            mvc
                .perform(get("/settings").session(signedIn(ADMIN)))
                .andReturn()
                .response.contentAsString

        val rows = ROW.findAll(page).map { it.value }.toList()
        assertEquals(3, rows.size)
        assertTrue("data-username=\"rob\"" in rows[0] && "Admin" in rows[0] && "[you]" in rows[0])
        assertFalse("Reset password" in rows[0] || "Delete" in rows[0])
        assertTrue("1970-01-01" in rows[1] && "Created" in rows[1])
        assertFalse("Admin" in rows[1] || "[you]" in rows[1])
        assertTrue("href=\"/settings?reset=2\"" in rows[1] && "/settings/users/2/reset" !in rows[1])
        assertTrue("href=\"/settings/users/2/delete\"" in rows[1])
        assertTrue("data-username=\"amy\"" in rows[2] && "/settings/users/3/delete" in rows[2])
    }

    @Test
    fun `a reset asks first, and the confirmation holds only the asked user's reset form`() {
        seed()

        val page =
            mvc
                .perform(get("/settings").param("reset", "2").session(signedIn(ADMIN)))
                .andReturn()
                .response.contentAsString

        val dialog = requireNotNull(Regex("<dialog.*?</dialog>", RegexOption.DOT_MATCHES_ALL).find(page)).value
        assertTrue("action=\"/settings/users/2/reset\"" in dialog && "name=\"_csrf\"" in dialog)
        assertFalse("/settings/users/3/reset" in page)
    }

    @Test
    fun `the notice is not shown without a deletion`() {
        seed()

        mvc
            .perform(get("/settings").session(signedIn(ADMIN)))
            .andExpect(content().string(not(containsString("Deleted user"))))
    }

    // ---- reset ----

    @Test
    fun `a reset answers directly with a one-time temporary password, uncached, and stores only its hash`() {
        seed()
        val old = users.hashOf(FRIEND)

        val result = reset("3")

        assertEquals(200, result.response.status)
        assertEquals("no-store", result.response.getHeader("Cache-Control"))
        val temporary = temporaryIn(result)
        assertTrue(Regex("$TEMPORARY(-$TEMPORARY){3}").matches(temporary), temporary)
        assertTrue(result.response.contentAsString.contains("shown once"))
        assertTrue(result.response.contentAsString.contains("href=\"/settings\""))
        assertNull(result.response.redirectedUrl)
        assertTrue(BCryptPasswordEncoder().matches(temporary, users.hashOf(FRIEND)))
        assertFalse(old == users.hashOf(FRIEND))
        assertFalse(users.hashOf(FRIEND).contains(temporary))
    }

    @Test
    fun `the temporary password logs the user in, the old one does not, and it is shown nowhere else`() {
        seed()
        val session = signedIn(ADMIN)
        val temporary = temporaryIn(reset("3", session))

        assertEquals("/feed", login(FRIEND, temporary).response.redirectedUrl)
        assertEquals("/login?error", login(FRIEND, PASSWORD).response.redirectedUrl)
        mvc
            .perform(get("/settings").session(session))
            .andExpect(content().string(not(containsString(temporary))))
        assertNull(session.getAttribute("temporaryPassword"))
    }

    @Test
    fun `every reset makes a new password and leaves the others alone`() {
        seed()
        val session = signedIn(ADMIN)
        val first = temporaryIn(reset("3", session))
        val second = temporaryIn(reset("3", session))

        assertFalse(first == second)
        assertEquals("/feed", login(MEMBER).response.redirectedUrl)
        assertEquals("/feed", login(ADMIN).response.redirectedUrl)
    }

    @Test
    fun `a reset ends the remember-me cookie of that user`() {
        seed()
        val cookie = loginRememberingMe(FRIEND).response.getCookie("remember-me")!!
        assertNotNull(cookie)

        reset("3")

        mvc.perform(get("/whoami").cookie(cookie)).andExpect(redirectedUrl(LOGIN_URL))
    }

    @Test
    fun `the admin cannot reset themselves`() {
        seed()
        val before = users.hashOf(ADMIN)

        assertEquals(400, reset("1").response.status)

        assertEquals(before, users.hashOf(ADMIN))
    }

    @ParameterizedTest
    @ValueSource(strings = ["9999", "nobody", "1.5"])
    fun `a reset of an unknown or non-numeric id is a 404`(id: String) {
        seed()

        assertEquals(404, reset(id).response.status)
    }

    // ---- delete ----

    @Test
    fun `the confirmation page names the user and deletes nothing`() {
        seed()

        mvc
            .perform(get("/settings/users/3/delete").session(signedIn(ADMIN)))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString(">amy<")))
            .andExpect(content().string(containsString("permanently deleted")))
            .andExpect(content().string(containsString("action=\"/settings/users/3/delete\"")))
            .andExpect(content().string(containsString("Delete permanently")))
            .andExpect(content().string(containsString("name=\"_csrf\"")))
            .andExpect(content().string(containsString("href=\"/settings\"")))
            .andExpect(content().string(containsString("Cancel")))

        assertNotNull(users.findByUsername(FRIEND))
    }

    @Test
    fun `a delete removes the user, redirects to settings and shows the notice once`() {
        seed()
        val session = signedIn(ADMIN)

        mvc.perform(post("/settings/users/3/delete").session(session)).andExpect(redirectedUrl("/settings"))

        assertNull(users.findByUsername(FRIEND))
        assertNotNull(users.findByUsername(MEMBER))
        mvc
            .perform(get("/settings").session(session))
            .andExpect(content().string(containsString("Deleted user amy")))
        mvc
            .perform(get("/settings").session(session))
            .andExpect(content().string(not(containsString("Deleted user"))))
    }

    @Test
    fun `the admin cannot delete themselves, on the page or by post`() {
        seed()
        val session = signedIn(ADMIN)

        mvc.perform(get("/settings/users/1/delete").session(session)).andExpect(status().isBadRequest)
        mvc.perform(post("/settings/users/1/delete").session(session)).andExpect(status().isBadRequest)

        assertNotNull(users.findByUsername(ADMIN))
    }

    @ParameterizedTest
    @ValueSource(strings = ["9999", "nobody"])
    fun `an unknown or non-numeric id is a 404 on both delete requests`(id: String) {
        seed()
        val session = signedIn(ADMIN)

        mvc.perform(get("/settings/users/$id/delete").session(session)).andExpect(status().isNotFound)
        mvc.perform(post("/settings/users/$id/delete").session(session)).andExpect(status().isNotFound)

        assertEquals(3, users.listAll().size)
    }

    @Test
    fun `the temporary password page carries no cache permission even for htmx`() {
        seed()

        mvc
            .perform(post("/settings/users/3/reset").session(signedIn(ADMIN)).header("HX-Request", "true"))
            .andExpect(header().string("Cache-Control", "no-store"))
    }
}
