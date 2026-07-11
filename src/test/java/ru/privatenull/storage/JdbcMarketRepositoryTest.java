package ru.privatenull.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcMarketRepositoryTest {
    @Test
    void sqliteBackendCreatesItsSchema(@TempDir Path directory) {
        try (JdbcMarketRepository repository = new JdbcMarketRepository(
                "org.sqlite.JDBC", "jdbc:sqlite:" + directory.resolve("market.db"), null, null,
                86_400_000L, Logger.getLogger("test"))) {
            assertTrue(repository.findAll().isEmpty());
        }
    }
}
