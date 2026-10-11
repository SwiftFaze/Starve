package dev.starve.infra

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DatabaseConfigTest {
    @Test
    fun `creates missing parent directories for the database file`(
        @TempDir tmp: Path,
    ) {
        val dbFile = tmp.resolve("a/b/starve.db")

        ensureParentDirectory(dbFile)

        assertTrue(Files.isDirectory(dbFile.parent))
    }

    @Test
    fun `accepts a bare relative file name`() {
        ensureParentDirectory(Path.of("starve-test.db"))
    }
}
