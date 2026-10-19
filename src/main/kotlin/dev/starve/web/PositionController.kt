package dev.starve.web

import dev.starve.application.ResumeService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** Where the deck stopped playing an item: sent while it plays, on pause, on Eject and at the end. */
@RestController
class PositionController(
    private val resume: ResumeService,
) {
    /** [at] and [duration] are whole seconds; a negative one is a 400, another user's or an unknown item a 404. */
    @PostMapping("/items/{id}/position")
    fun stoppedAt(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        @RequestParam at: Long,
        @RequestParam(required = false) duration: Long?,
    ): ResponseEntity<Void> {
        if (at < 0 || (duration != null && duration < 0)) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        if (!resume.stoppedAt(owner, id, at, duration)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return ResponseEntity.noContent().build()
    }
}
