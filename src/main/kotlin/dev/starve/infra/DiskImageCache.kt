package dev.starve.infra

import dev.starve.application.ImageCache
import dev.starve.application.ProxiedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.Clock
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

/**
 * The image cache as files in [dir], beside the database: each image is named by the SHA-256 of its URL, with an
 * extension for its media type. A file's modified time is when it was last served, so pruning drops the least
 * recently used first.
 */
class DiskImageCache(
    private val dir: Path,
    private val clock: Clock = Clock.systemUTC(),
) : ImageCache {
    override fun find(url: String): ProxiedImage? {
        val name = nameOf(url)
        val file = EXTENSIONS.values.map { dir.resolve("$name.$it") }.firstOrNull { Files.isRegularFile(it) } ?: return null
        return try {
            val image = ProxiedImage(Files.readAllBytes(file), typeOf(file))
            Files.setLastModifiedTime(file, FileTime.from(clock.instant()))
            image
        } catch (_: IOException) {
            null
        }
    }

    override fun store(
        url: String,
        image: ProxiedImage,
    ) {
        val extension = EXTENSIONS[image.contentType] ?: return
        Files.createDirectories(dir)
        val file = dir.resolve("${nameOf(url)}.$extension")
        val partial = Files.createTempFile(dir, "partial-", ".tmp")
        Files.write(partial, image.bytes)
        Files.move(partial, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        Files.setLastModifiedTime(file, FileTime.from(clock.instant()))
    }

    override fun prune(
        keep: Set<String>,
        maxBytes: Long,
    ): Int {
        if (!Files.isDirectory(dir)) return 0
        val kept = keep.mapTo(HashSet(), ::nameOf)
        val (wanted, unwanted) = dir.listDirectoryEntries().filter { Files.isRegularFile(it) }.partition { it.nameWithoutExtension in kept }
        unwanted.forEach(Files::deleteIfExists)
        var total = wanted.sumOf(Files::size)
        val overflow =
            wanted.sortedBy { Files.getLastModifiedTime(it) }.takeWhile { file ->
                (total > maxBytes).also { if (it) total -= Files.size(file) }
            }
        overflow.forEach(Files::deleteIfExists)
        return unwanted.size + overflow.size
    }

    private fun typeOf(file: Path): String = EXTENSIONS.entries.first { it.value == file.extension }.key

    private fun nameOf(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        /** The extension each served media type is stored under; the two icon types share one, read back as the first. */
        val EXTENSIONS =
            linkedMapOf(
                "image/jpeg" to "jpg",
                "image/png" to "png",
                "image/gif" to "gif",
                "image/webp" to "webp",
                "image/avif" to "avif",
                "image/bmp" to "bmp",
                "image/svg+xml" to "svg",
                "image/x-icon" to "ico",
                "image/vnd.microsoft.icon" to "ico",
            )
    }
}
