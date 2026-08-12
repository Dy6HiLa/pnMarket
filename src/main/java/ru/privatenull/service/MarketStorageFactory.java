package ru.privatenull.service;

import org.bukkit.configuration.file.FileConfiguration;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.storage.*;

import java.io.File;
import java.util.Locale;

public final class MarketStorageFactory {
    private final PnMarketPlugin plugin;

    public MarketStorageFactory(PnMarketPlugin plugin) {
        this.plugin = plugin;
    }

    public MarketStorage open(boolean donate) {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "sqlite" -> sqlite(config, donate);
            case "mysql" -> mysql(config, donate);
            case "mongodb", "mongo" -> mongo(config, donate);
            default -> throw new IllegalArgumentException("Неизвестный тип хранилища: " + type);
        };
    }

    private MarketStorage sqlite(FileConfiguration config, boolean donate) {
        File database = new File(plugin.getDataFolder(), config.getString("storage.sqlite.file", "market.db"));
        return new JdbcMarketRepository("org.sqlite.JDBC", "jdbc:sqlite:" + database.getAbsolutePath(),
                null, null, legacyExpiry(), plugin.getLogger(), table(donate));
    }

    private MarketStorage mysql(FileConfiguration config, boolean donate) {
        String url = config.getString("storage.mysql.url", "");
        if (url == null || url.isBlank()) {
            url = "jdbc:mysql://" + config.getString("storage.mysql.host", "localhost") + ":"
                    + config.getInt("storage.mysql.port", 3306) + "/"
                    + config.getString("storage.mysql.database", "minecraft")
                    + "?useUnicode=true&characterEncoding=utf8&useSSL=false";
        }
        return new JdbcMarketRepository("com.mysql.cj.jdbc.Driver", url,
                config.getString("storage.mysql.username", "root"),
                config.getString("storage.mysql.password", ""), legacyExpiry(),
                plugin.getLogger(), table(donate));
    }

    private MarketStorage mongo(FileConfiguration config, boolean donate) {
        String uri = System.getenv("PNMARKET_MONGO_URI");
        if (uri == null || uri.isBlank()) uri = config.getString("storage.mongo.uri", "mongodb://localhost:27017");
        String collection = config.getString("storage.mongo.collection", "auction") + (donate ? "_donate" : "");
        return new MarketRepository(uri, config.getString("storage.mongo.database", "minecraft"),
                collection, legacyExpiry(), plugin.getLogger());
    }

    private String table(boolean donate) {
        return donate ? "pnmarket_donate_listings" : "pnmarket_listings";
    }

    private long legacyExpiry() {
        return 86_400_000L;
    }
}
