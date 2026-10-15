package dev.starve.web

import dev.starve.application.OWNER
import dev.starve.application.UserRepository
import dev.starve.domain.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class CurrentUserIdResolverTest {
    private val users =
        object : UserRepository {
            override fun exists() = true

            override fun findByUsername(username: String) =
                if (username.equals("amy", ignoreCase = true)) User(OWNER, "Amy", "hash", admin = false) else null

            override fun insertFirstAdmin(
                username: String,
                passwordHash: String,
                at: Instant,
            ) = false

            override fun insert(
                username: String,
                passwordHash: String,
                at: Instant,
            ) = error("unused")
        }
    private val resolver = CurrentUserIdResolver(users)
    private val request = ServletWebRequest(MockHttpServletRequest())

    @Suppress("unused")
    private fun annotated(
        @CurrentUserId owner: Long,
        plain: Long,
    ) = owner + plain

    private val method =
        CurrentUserIdResolverTest::class.java.getDeclaredMethod(
            "annotated",
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
        )

    private fun parameter(index: Int) = MethodParameter(method, index)

    private fun resolve() = resolver.resolveArgument(parameter(0), null, request, null)

    private fun signIn(username: String) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken.authenticated(username, "x", emptyList())
    }

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `only parameters marked as the current user are resolved`() {
        assertTrue(resolver.supportsParameter(parameter(0)))
        assertFalse(resolver.supportsParameter(parameter(1)))
    }

    @Test
    fun `the id is that of the account the signed-in name belongs to, ignoring case`() {
        signIn("AMY")

        assertEquals(OWNER, resolve())
    }

    @Test
    fun `nobody signed in, or an account that no longer exists, is unauthorised`() {
        val anonymous = assertThrows(ResponseStatusException::class.java) { resolve() }
        signIn("gone")
        val deleted = assertThrows(ResponseStatusException::class.java) { resolve() }

        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, deleted.statusCode)
    }

    @Test
    fun `it registers itself as an argument resolver`() {
        val registered = mutableListOf<HandlerMethodArgumentResolver>()

        resolver.addArgumentResolvers(registered)

        assertEquals(listOf<HandlerMethodArgumentResolver>(resolver), registered)
    }
}
