package dev.starve.infra

import dev.starve.application.AppMetadata
import dev.starve.application.SetupService
import dev.starve.application.SignupSettings
import dev.starve.application.UserAdminRepository
import dev.starve.application.UserRepository
import dev.starve.domain.User
import dev.starve.domain.UserAccount
import dev.starve.web.AccountController
import dev.starve.web.LoginController
import dev.starve.web.SettingsController
import dev.starve.web.SetupController
import dev.starve.web.SignupController
import dev.starve.web.UserAdminController
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

const val ADMIN = "rob"
const val MEMBER = "sam"
const val PASSWORD = "correct horse battery"
const val WRONG_PASSWORD = "wrong horse battery"
const val CSRF_COOKIE = "XSRF-TOKEN"
const val CSRF_HEADER = "X-CSRF-TOKEN"
const val CSRF_TOKEN = "a-known-csrf-token"

/** Accounts held in memory, looked up ignoring case as the real repository does. */
class FakeUsers :
    UserRepository,
    UserAdminRepository {
    private val accounts = mutableListOf<User>()
    private var lastId = 0L

    fun add(
        username: String,
        admin: Boolean,
    ) {
        accounts += User(++lastId, username, HASHER.encode(PASSWORD), admin)
    }

    /** Removes the account behind the application's back, as another admin's deletion does. */
    fun remove(username: String) {
        accounts.removeIf { it.username == username }
    }

    fun hashOf(username: String) = findByUsername(username)!!.passwordHash

    override fun listAll() = accounts.map { UserAccount(it.id, it.username, it.admin, Instant.EPOCH) }

    override fun findById(id: Long) = accounts.firstOrNull { it.id == id }

    override fun updatePasswordHash(
        id: Long,
        passwordHash: String,
    ) {
        accounts.replaceAll { if (it.id == id) it.copy(passwordHash = passwordHash) else it }
    }

    override fun delete(id: Long) = accounts.removeIf { it.id == id }

    override fun exists() = accounts.isNotEmpty()

    override fun findByUsername(username: String) = accounts.firstOrNull { it.username.equals(username, true) }

    override fun insertFirstAdmin(
        username: String,
        passwordHash: String,
        at: Instant,
    ): Boolean {
        if (exists()) return false
        accounts += User(++lastId, username, passwordHash, true)
        return true
    }

    override fun insert(
        username: String,
        passwordHash: String,
        at: Instant,
    ): User = User(++lastId, username, passwordHash, false).also { accounts += it }

    override fun insertIfFree(
        username: String,
        passwordHash: String,
        at: Instant,
    ): User? = if (findByUsername(username) != null) null else insert(username, passwordHash, at)

    private companion object {
        val HASHER = BCryptPasswordEncoder(4)
    }
}

class FakeMetadata : AppMetadata {
    private val values = ConcurrentHashMap<String, String>()

    override fun find(key: String): String? = values[key]

    override fun putIfAbsent(
        key: String,
        value: String,
    ) = values.putIfAbsent(key, value) == null

    override fun put(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}

/**
 * Answers every path the security chain lets through, so a status says what the chain decided. The condition keeps
 * component scanning in the full application (acceptance tests) from picking up its catch-all mapping.
 */
@ConditionalOnProperty("starve.test.pages")
@RestController
class Pages {
    @GetMapping("/whoami")
    fun whoami(authentication: Authentication): String {
        val roles = authentication.authorities.joinToString(",") { it.authority }
        return "${authentication.name}:$roles"
    }

    @Suppress("FunctionOnlyReturningConstant") // the body is irrelevant; the status is what the tests read
    @GetMapping("/**")
    fun anyPage() = "page"
}

@TestConfiguration
class SecurityTestBeans {
    @Bean
    fun users() = FakeUsers()

    @Bean
    fun metadata() = FakeMetadata()

    @Bean
    fun clock(): Clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC)

    @Bean
    fun pages() = Pages()
}

/**
 * The real security chain and account controllers behind MockMvc, with in-memory accounts. Each test gets its own
 * context, because mutation testing would otherwise reuse a context built before the mutant was applied.
 */
@WebMvcTest(
    controllers = [
        LoginController::class,
        SetupController::class,
        SignupController::class,
        SettingsController::class,
        AccountController::class,
        UserAdminController::class,
    ],
)
@Import(SecurityConfig::class, AccountConfig::class, SecurityTestBeans::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
abstract class SecurityTestBase {
    @Autowired
    protected lateinit var mvc: MockMvc

    @Autowired
    protected lateinit var users: FakeUsers

    @Autowired
    protected lateinit var setup: SetupService

    @Autowired
    protected lateinit var signups: SignupSettings

    protected fun withAccounts() {
        users.add(ADMIN, admin = true)
        users.add(MEMBER, admin = false)
    }

    /** As a container with the default servlet maps it: the whole path is the servlet path. */
    protected fun request(
        method: HttpMethod,
        path: String,
    ): MockHttpServletRequestBuilder {
        val servletPath = if (path == "/") "" else path
        return MockMvcRequestBuilders.request(method, path).servletPath(servletPath)
    }

    protected fun get(path: String) = request(HttpMethod.GET, path)

    /** A post as htmx and the forms send it: the CSRF cookie and the matching header. */
    protected fun post(path: String) =
        request(HttpMethod.POST, path)
            .cookie(Cookie(CSRF_COOKIE, CSRF_TOKEN))
            .header(CSRF_HEADER, CSRF_TOKEN)

    protected fun postWithoutCsrf(path: String) = request(HttpMethod.POST, path)

    protected fun clientAt(address: String) = RequestPostProcessor { it.apply { remoteAddr = address } }

    private fun loginForm(
        username: String,
        password: String,
        session: MockHttpSession,
        from: String,
    ) = post("/login")
        .session(session)
        .with(clientAt(from))
        .param("username", username)
        .param("password", password)

    protected fun login(
        username: String,
        password: String = PASSWORD,
        session: MockHttpSession = MockHttpSession(),
        from: String = "203.0.113.7",
    ): MvcResult = mvc.perform(loginForm(username, password, session, from)).andReturn()

    protected fun loginRememberingMe(
        username: String,
        session: MockHttpSession = MockHttpSession(),
    ): MvcResult {
        val form = loginForm(username, PASSWORD, session, "203.0.113.7")
        return mvc.perform(form.param("remember-me", "on")).andReturn()
    }

    protected fun failLogin(
        username: String,
        times: Int,
        from: String = "203.0.113.7",
    ) = repeat(times) { login(username, WRONG_PASSWORD, from = from) }
}
