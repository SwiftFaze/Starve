package dev.starve.infra

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `data source creates the database directory`(
        @TempDir tmp: Path,
    ) {
        val dbFile = tmp.resolve("nested/dir/starve.db")

        (DatabaseConfig().dataSource(dbFile.toString()) as HikariDataSource).use {
            assertTrue(Files.isDirectory(dbFile.parent))
        }
    }

    @Test
    fun `data source uses a single connection`(
        @TempDir tmp: Path,
    ) {
        val dbFile = tmp.resolve("starve.db")

        (DatabaseConfig().dataSource(dbFile.toString()) as HikariDataSource).use {
            assertEquals(1, it.maximumPoolSize)
        }
    }
}
