package dev.starve.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * The htmx event a response raises when its request marked an item read. The timeline listens for it and fetches
 * its unread counts again ("/feed/counts"), so the obi and the group links follow the reading without a page load.
 */
internal const val READ_EVENT = "starve:read"

internal const val HX_TRIGGER = "HX-Trigger"

/** Marks a handler that renders a view after marking an item read, so its response raises [READ_EVENT]. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AnnouncesRead

/**
 * Raises [READ_EVENT] on the response of an [AnnouncesRead] handler. It runs after the handler and before its view
 * renders, so a handler that failed (a 404 throws) raises nothing. A handler that writes its own body commits the
 * response before this runs, so it sets the header itself (see HomeController.markRead).
 */
@Component
class ReadEventInterceptor :
    HandlerInterceptor,
    WebMvcConfigurer {
    override fun postHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        modelAndView: ModelAndView?,
    ) {
        if (handler is HandlerMethod && handler.hasMethodAnnotation(AnnouncesRead::class.java)) {
            response.setHeader(HX_TRIGGER, READ_EVENT)
        }
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(this)
    }
}
