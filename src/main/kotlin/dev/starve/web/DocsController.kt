package dev.starve.web

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/** The public install and setup guide: open to everyone, and before the first account exists, since it explains setup. */
@Controller
class DocsController {
    @GetMapping("/docs")
    fun docs(): String = "docs"
}
