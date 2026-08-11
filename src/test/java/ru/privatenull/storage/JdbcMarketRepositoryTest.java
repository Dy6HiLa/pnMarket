package ru.privatenull.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.model.MarketListing;

import java.nio.file.Path;
import java.util.UUID;
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

    @Test
    void sqlitePersistsListingCurrency(@TempDir Path directory) throws Exception {
        try (JdbcMarketRepository repository = new JdbcMarketRepository(
                "org.sqlite.JDBC", "jdbc:sqlite:" + directory.resolve("market.db"), null, null,
                86_400_000L, Logger.getLogger("test"))) {
            MarketListing listing = repository.create(UUID.randomUUID(), new ItemStack(Material.STONE),
                    "tokens", 2.0, 1, System.currentTimeMillis());
            assertTrue(repository.findById(listing.id()).orElseThrow().currencyId().equals("tokens"));
        }
    }
}
