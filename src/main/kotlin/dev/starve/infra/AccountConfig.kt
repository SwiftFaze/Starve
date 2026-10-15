package dev.starve.infra

import dev.starve.application.AccountService
import dev.starve.application.AppMetadata
import dev.starve.application.FailureThrottle
import dev.starve.application.RememberMeKeyProvider
import dev.starve.application.SetupService
import dev.starve.application.SignupService
import dev.starve.application.SignupSettings
import dev.starve.application.UserAdminRepository
import dev.starve.application.UserAdminService
import dev.starve.application.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.Clock

/** Wires the account services; the security chain itself lives in [SecurityConfig]. */
@Configuration
class AccountConfig(
    private val clock: Clock,
) {
    @Bean
    fun rememberMeKeys(metadata: AppMetadata) = RememberMeKeyProvider(metadata)

    @Bean
    fun failureThrottle() = FailureThrottle(clock)

    @Bean
    fun setupService(
        users: UserRepository,
        encoder: PasswordEncoder,
        throttle: FailureThrottle,
    ) = SetupService(users, { encoder.encode(it) }, throttle, clock)

    @Bean
    fun signupSettings(metadata: AppMetadata) = SignupSettings(metadata)

    @Bean
    fun signupService(
        users: UserRepository,
        encoder: PasswordEncoder,
        throttle: FailureThrottle,
        settings: SignupSettings,
    ) = SignupService(users, { encoder.encode(it) }, throttle, clock, settings)

    @Bean
    fun accountService(
        users: UserAdminRepository,
        encoder: PasswordEncoder,
        throttle: FailureThrottle,
    ) = AccountService(users, { encoder.encode(it) }, { password, hash -> encoder.matches(password, hash) }, throttle)

    @Bean
    fun userAdminService(
        users: UserAdminRepository,
        encoder: PasswordEncoder,
    ) = UserAdminService(users, { encoder.encode(it) })
}

/** Logs this run's setup code once the application is ready, but only while no account exists. */
@Component
class SetupCodeLogger(
    private val setup: SetupService,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun logSetupCode() {
        setup.startRun()?.let { log.info("Starve setup code: {}", it) }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SetupCodeLogger::class.java)
    }
}
