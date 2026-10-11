package dev.starve.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

@Configuration
class DatabaseConfig {
    @Bean(destroyMethod = "close")
    fun dataSource(
        @Value("\${starve.db.path:./data/starve.db}") dbPath: String,
    ): DataSource {
        ensureParentDirectory(Path.of(dbPath))
        val config =
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:$dbPath"
                // SQLite allows one writer; a single connection avoids "database is locked".
                maximumPoolSize = 1
            }
        return HikariDataSource(config)
    }
}

internal fun ensureParentDirectory(dbFile: Path) {
    dbFile.toAbsolutePath().parent?.let { Files.createDirectories(it) }
}
