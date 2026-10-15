package dev.starve.web

import dev.starve.application.UserRepository
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Marks a controller parameter that receives the id of the signed-in user, who owns everything the call touches. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentUserId

/** Resolves [CurrentUserId] parameters from the security context; the application layer only ever sees the id. */
@Component
class CurrentUserIdResolver(
    private val users: UserRepository,
) : HandlerMethodArgumentResolver,
    WebMvcConfigurer {
    override fun supportsParameter(parameter: MethodParameter) = parameter.hasParameterAnnotation(MARKER)

    /** A session whose account no longer exists is treated as signed out. */
    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Long {
        val username = SecurityContextHolder.getContext().authentication?.name
        return username?.let { users.findByUsername(it)?.id } ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers += this
    }

    private companion object {
        val MARKER = CurrentUserId::class.java
    }
}
