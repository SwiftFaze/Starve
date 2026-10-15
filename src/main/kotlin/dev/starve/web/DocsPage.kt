package dev.starve.web

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * The public self-hosting guide at "/docs": a static page, open to everyone and before the first account
 * exists, since it explains setup.
 */
@Configuration
class DocsPage : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController("/docs").setViewName("docs")
    }
}
