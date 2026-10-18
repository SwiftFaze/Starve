package dev.starve.infra

import dev.starve.application.GroupRepository
import dev.starve.application.TagRepository
import dev.starve.application.TagService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Wires the keyword tags use case. */
@Configuration
class TagConfig {
    @Bean
    fun tagService(
        tags: TagRepository,
        groups: GroupRepository,
    ) = TagService(tags, groups)
}
