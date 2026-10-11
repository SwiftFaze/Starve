package dev.starve.web

import dev.starve.application.ImportOutcome
import dev.starve.application.ImportService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.ModelAndView

const val UNRECOGNISED_FILE = "unrecognised file: expected a YouTube subscriptions CSV or an OPML file"
const val FILE_TOO_LARGE = "file is too large (max 1 MB)"

@Controller
class ImportController(
    private val imports: ImportService,
) {
    @GetMapping("/import")
    fun page(): ModelAndView = ModelAndView("import")

    @PostMapping("/import")
    fun upload(
        @RequestParam file: MultipartFile,
        model: Model,
    ): String {
        when (val outcome = imports.importFile(file.bytes)) {
            ImportOutcome.Unrecognised -> model.addAttribute("message", UNRECOGNISED_FILE)
            is ImportOutcome.Done -> model.addAttribute("summary", outcome.summary)
        }
        return "import"
    }
}

/** Shows an over-size upload on the import page; the multipart limit trips before any controller is chosen. */
@ControllerAdvice
class UploadLimitAdvice {
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(): ModelAndView = ModelAndView("import", "message", FILE_TOO_LARGE)
}
