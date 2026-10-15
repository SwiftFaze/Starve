package dev.starve.infra

import dev.starve.application.FailureThrottle
import dev.starve.application.RememberMeKeyProvider
import dev.starve.application.SetupService
import dev.starve.application.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.RememberMeAuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer
import org.springframework.security.config.annotation.web.configurers.RememberMeConfigurer
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler

/** A remembered login lasts 30 days. */
private const val REMEMBER_ME_SECONDS = 30 * 24 * 60 * 60

/** The CSRF header htmx and the acceptance client send; the token itself lives in the XSRF-TOKEN cookie. */
private const val CSRF_HEADER = "X-CSRF-TOKEN"

private typealias Registry = AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry

private val PUBLIC_PATHS =
    listOf(
        "/",
        "/setup",
        "/login",
        "/signup",
        "/error",
        "/actuator/health",
        "/*.css",
        "/*.js",
        "/fonts/**",
        "/favicon.ico",
    )

/**
 * What the login chain needs: the throttled password check, the account lookup, and the remember-me key.
 * The remember-me key is kept in app_metadata, so remembered logins survive a restart.
 */
class LoginAuthentication(
    val manager: AuthenticationManager,
    val users: UserDetailsService,
    val rememberMeKey: String,
)

@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(users: UserRepository): UserDetailsService = AccountDetailsService(users)

    @Bean
    fun loginAuthentication(
        users: UserDetailsService,
        encoder: PasswordEncoder,
        throttle: FailureThrottle,
        keys: RememberMeKeyProvider,
    ): LoginAuthentication {
        // Explicit and parentless, so no other provider can accept a password the throttle has refused.
        val password = DaoAuthenticationProvider(users).apply { setPasswordEncoder(encoder) }
        val rememberMeKey = keys.key()
        val manager =
            ProviderManager(
                ThrottledLoginProvider(password, throttle),
                RememberMeAuthenticationProvider(rememberMeKey),
            )
        return LoginAuthentication(manager, users, rememberMeKey)
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        users: UserRepository,
        setup: SetupService,
        login: LoginAuthentication,
    ): SecurityFilterChain {
        http
            .csrf {
                it
                    .csrfTokenRepository(csrfTokens())
                    .csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
            }.addFilterAfter(CsrfCookieWriter(), CsrfFilter::class.java)
            .addFilterBefore(SetupGate(setup), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(DeletedUserFilter(users), RememberMeAuthenticationFilter::class.java)
            .authorizeHttpRequests { authorise(it) }
            .authenticationManager(login.manager)
            .formLogin { configureFormLogin(it) }
            .rememberMe { configureRememberMe(it, login) }
            .logout { configureLogout(it) }
            .requestCache { it.requestCache(htmxSafeRequestCache()) }
            .exceptionHandling { it.authenticationEntryPoint(loginEntryPoint()) }
        return http.build()
    }

    private fun authorise(registry: Registry) {
        PUBLIC_PATHS.forEach { registry.requestMatchers(it).permitAll() }
        registry.requestMatchers("/settings", "/settings/**").hasRole("ADMIN")
        registry.anyRequest().authenticated()
    }

    private fun configureFormLogin(form: FormLoginConfigurer<HttpSecurity>) {
        form
            .loginPage("/login")
            .loginProcessingUrl("/login")
            .failureHandler(LoginFailureHandler())
            .defaultSuccessUrl("/feed", false)
            .permitAll()
    }

    private fun configureRememberMe(
        config: RememberMeConfigurer<HttpSecurity>,
        login: LoginAuthentication,
    ) {
        config
            .key(login.rememberMeKey)
            .rememberMeParameter("remember-me")
            .tokenValiditySeconds(REMEMBER_ME_SECONDS)
            .userDetailsService(login.users)
    }

    private fun configureLogout(config: LogoutConfigurer<HttpSecurity>) {
        config
            .logoutUrl("/logout")
            .logoutSuccessUrl("/")
            .deleteCookies("JSESSIONID", "remember-me")
            .invalidateHttpSession(true)
    }

    private fun csrfTokens() = CookieCsrfTokenRepository.withHttpOnlyFalse().apply { setHeaderName(CSRF_HEADER) }
}
