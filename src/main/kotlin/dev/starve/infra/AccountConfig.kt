package dev.starve.infra

import dev.starve.application.FailureThrottle
import dev.starve.application.SetupService
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
class AccountConfig {
    @Bean
    fun failureThrottle(clock: Clock) = FailureThrottle(clock)

    @Bean
    fun setupService(
        users: UserRepository,
        encoder: PasswordEncoder,
        throttle: FailureThrottle,
        clock: Clock,
    ) = SetupService(users, { encoder.encode(it) }, throttle, clock)
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
