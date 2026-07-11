package ru.privatenull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.config.GuiLabels;
import ru.privatenull.config.MessagesConfig;
import ru.privatenull.lifecycle.PluginBanner;
import ru.privatenull.localization.LangRu;
import ru.privatenull.localization.ItemLocalization;
import ru.privatenull.command.MarketCommand;
import ru.privatenull.gui.MarketGuiController;
import ru.privatenull.gui.MarketInventoryListener;
import ru.privatenull.market.MarketSync;
import ru.privatenull.market.MarketCategories;
import ru.privatenull.model.MarketListing;
import ru.privatenull.storage.MarketRepository;
import ru.privatenull.storage.MarketStorage;
import ru.privatenull.storage.JdbcMarketRepository;
import ru.privatenull.update.UpdateChecker;

import java.io.IOException;
import java.io.File;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PnMarketPlugin extends JavaPlugin {
    public static final String SUPPORT_DISCORD = "https://discord.gg/rRbzq6cnc6";
    private static final long EXPIRY_MILLIS = 24L * 60L * 60L * 1000L;

    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0");
    private MarketStorage repository;
    private Economy economy;
    private Permission permission;
    private MessagesConfig messages;
    private GuiLabels guiLabels;
    private MarketSync sync;
    private MarketGuiController gui;
    private MarketCategories categories;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessagesConfig(this);
        guiLabels = new GuiLabels(messages);
        LangRu.init(this);

        if (!setupEconomy()) {
            getLogger().severe("Vault не найден, плагин отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!setupRepository()) {
            getLogger().severe("Хранилище аукциона не инициализировано, плагин отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        setupPermissions();

        categories = MarketCategories.load(getConfig(), getLogger());
        sync = new MarketSync(this, repository);
        gui = new MarketGuiController(this, repository, economy, messages, guiLabels, categories);

        var command = Objects.requireNonNull(getCommand("ah"), "Команда ah отсутствует в plugin.yml");
        var commandHandler = new MarketCommand(this);
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        getServer().getPluginManager().registerEvents(new MarketInventoryListener(this), this);

        updateChecker = new UpdateChecker(this, messages);
        updateChecker.start();
        PluginBanner.enabled(this, SUPPORT_DISCORD);
    }

    @Override
    public void onDisable() {
        if (sync != null) sync.cancel();
        if (updateChecker != null) updateChecker.cancel();
        if (repository != null) repository.close();
        PluginBanner.disabled(this, SUPPORT_DISCORD);
    }

    public MessagesConfig messages() {
        return messages;
    }

    public MarketSync marketSync() {
        return sync;
    }

    public MarketGuiController gui() {
        return gui;
    }

    public void reloadRuntime() {
        reloadConfig();
        messages.reload();
        LangRu.init(this);
        categories = MarketCategories.load(getConfig(), getLogger());
        gui = new MarketGuiController(this, repository, economy, messages, guiLabels, categories);
        if (sync != null) sync.cancel();
        sync = new MarketSync(this, repository);
        if (updateChecker != null) updateChecker.cancel();
        updateChecker = new UpdateChecker(this, messages);
        updateChecker.start();
        sync.refreshAsync();
    }

    public List<MarketListing> activeListings() {
        return gui.activeListings();
    }

    public void openAuction(Player player) {
        gui.openAuction(player);
    }

    public void openAuctionSearch(Player player, String query) {
        gui.openAuctionSearch(player, query);
    }

    public void openSellerGui(Player player, UUID sellerId) {
        gui.openSellerGui(player, sellerId);
    }

    public void renderAllViews() {
        if (gui != null) gui.renderAllViews();
    }

    public void notifyUpdate(Player player) {
        if (updateChecker != null) updateChecker.notifyOnJoin(player);
    }

    public void sell(Player player, String rawPrice) {
        double totalPrice;
        try {
            totalPrice = Double.parseDouble(rawPrice.replace(',', '.'));
        } catch (NumberFormatException exception) {
            reject(player, "error.invalid-price");
            return;
        }
        if (!Double.isFinite(totalPrice) || totalPrice <= 0) {
            reject(player, "error.price-positive");
            return;
        }
        double minimumPrice = Math.max(0.0, getConfig().getDouble("listing-price.minimum", 1.0));
        double maximumPrice = getConfig().getDouble("listing-price.maximum", 0.0);
        if (totalPrice < minimumPrice) {
            player.sendMessage(messages.message("error.price-too-low", Map.of("price", moneyFormat.format(minimumPrice))));
            return;
        }
        if (maximumPrice > 0.0 && totalPrice > maximumPrice) {
            player.sendMessage(messages.message("error.price-too-high", Map.of("price", moneyFormat.format(maximumPrice))));
            return;
        }
        int limit = listingLimit(player);
        if (repository.countActiveListings(player.getUniqueId()) >= limit) {
            player.sendMessage(messages.message("error.listing-limit", Map.of("limit", limit)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            reject(player, "error.item-required");
            return;
        }
        int amount = hand.getAmount();
        if (amount <= 0) {
            reject(player, "error.invalid-amount");
            return;
        }
        ItemStack storedItem = hand.clone();
        try {
            MarketListing listing = repository.create(player.getUniqueId(), storedItem, totalPrice / amount,
                    amount, System.currentTimeMillis());
            sync.listingCreated(listing);
        } catch (IOException | RuntimeException exception) {
            getLogger().warning("Не удалось создать лот: " + exception.getMessage());
            reject(player, "error.serialization");
            return;
        }
        player.getInventory().setItemInMainHand(null);
        Component itemName = ItemLocalization.getNameComponent(storedItem);
        player.sendMessage(component(messages.message("notification.listed-prefix"))
                .append(itemName.color(NamedTextColor.YELLOW))
                .append(component(messages.message("notification.price-separator")))
                .append(Component.text(moneyFormat.format(totalPrice) + "⛁", NamedTextColor.GREEN)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> registration =
                getServer().getServicesManager().getRegistration(Economy.class);
        economy = registration == null ? null : registration.getProvider();
        return economy != null;
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> registration =
                getServer().getServicesManager().getRegistration(Permission.class);
        permission = registration == null ? null : registration.getProvider();
        return permission != null;
    }

    private boolean setupRepository() {
        try {
            FileConfiguration config = getConfig();
            String type = config.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
            repository = switch (type) {
                case "sqlite" -> createSqliteRepository(config);
                case "mysql" -> createMySqlRepository(config);
                case "mongodb", "mongo" -> createMongoRepository(config);
                default -> throw new IllegalArgumentException("Неизвестный тип хранилища: " + type);
            };
            getLogger().info("Хранилище аукциона: " + type);
            return true;
        } catch (RuntimeException exception) {
            getLogger().severe("Не удалось открыть хранилище аукциона: " + exception.getMessage());
            return false;
        }
    }

    private MarketStorage createSqliteRepository(FileConfiguration config) {
        String fileName = config.getString("storage.sqlite.file", "market.db");
        File databaseFile = new File(getDataFolder(), fileName);
        return new JdbcMarketRepository("org.sqlite.JDBC", "jdbc:sqlite:" + databaseFile.getAbsolutePath(),
                null, null, EXPIRY_MILLIS, getLogger());
    }

    private MarketStorage createMySqlRepository(FileConfiguration config) {
        String url = config.getString("storage.mysql.url", "");
        if (url == null || url.isBlank()) {
            String host = config.getString("storage.mysql.host", "localhost");
            int port = config.getInt("storage.mysql.port", 3306);
            String database = config.getString("storage.mysql.database", "minecraft");
            url = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useUnicode=true&characterEncoding=utf8&useSSL=false";
        }
        return new JdbcMarketRepository("com.mysql.cj.jdbc.Driver", url,
                config.getString("storage.mysql.username", "root"),
                config.getString("storage.mysql.password", ""), EXPIRY_MILLIS, getLogger());
    }

    private MarketStorage createMongoRepository(FileConfiguration config) {
        String uri = System.getenv("PNMARKET_MONGO_URI");
        if (uri == null || uri.isBlank()) uri = config.getString("storage.mongo.uri", "mongodb://localhost:27017");
        return new MarketRepository(uri, config.getString("storage.mongo.database", "minecraft"),
                config.getString("storage.mongo.collection", "auction"), EXPIRY_MILLIS, getLogger());
    }

    private int listingLimit(Player player) {
        String group = "default";
        if (permission != null) {
            try {
                String primaryGroup = permission.getPrimaryGroup(player);
                if (primaryGroup != null && !primaryGroup.isBlank()) {
                    group = primaryGroup.toLowerCase(Locale.ROOT);
                }
            } catch (RuntimeException exception) {
                getLogger().warning("Не удалось определить группу игрока: " + exception.getMessage());
            }
        }
        int fallback = getConfig().getInt("limits.default", 3);
        return Math.max(0, getConfig().getInt("limits." + group, fallback));
    }

    private void reject(Player player, String messageKey) {
        player.sendMessage(messages.message(messageKey));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
    }

    private Component component(String value) {
        return LegacyComponentSerializer.legacySection().deserialize(value);
    }
}
