package ru.privatenull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bstats.bukkit.Metrics;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
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
import ru.privatenull.currency.PlayerPointsPayment;
import ru.privatenull.currency.VaultPayment;
import ru.privatenull.gui.MarketGuiController;
import ru.privatenull.gui.MarketInventoryListener;
import ru.privatenull.market.MarketBundle;
import ru.privatenull.market.MarketSync;
import ru.privatenull.market.MarketCategories;
import ru.privatenull.model.MarketListing;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pnlibrary.update.UpdateChecker;
import ru.privatenull.pnlibrary.update.UpdateSettings;
import ru.privatenull.storage.MarketRepository;
import ru.privatenull.storage.MarketStorage;
import ru.privatenull.storage.JdbcMarketRepository;

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
    private static final String GITHUB_REPOSITORY = "Dy6HiLa/pnMarket";
    private static final int BSTATS_PLUGIN_ID = 32716;
    private static final long EXPIRY_MILLIS = 24L * 60L * 60L * 1000L;

    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0");
    private MarketStorage repository;
    private MarketStorage donateRepository;
    private Economy economy;
    private PlayerPointsAPI playerPoints;
    private Permission permission;
    private MessagesConfig messages;
    private GuiLabels guiLabels;
    private MarketSync sync;
    private MarketSync donateSync;
    private MarketGuiController gui;
    private MarketGuiController donateGui;
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
        setupPlayerPoints();
        if (playerPoints != null && !setupDonateRepository()) {
            getLogger().warning("Не удалось открыть хранилище донат-аукциона; /dah отключён.");
        }

        categories = MarketCategories.load(getConfig(), getLogger());
        sync = new MarketSync(this, repository);
        gui = new MarketGuiController(this, repository, new VaultPayment(economy), messages, guiLabels, categories, sync, false);
        if (donateRepository != null && playerPoints != null) {
            donateSync = new MarketSync(this, donateRepository);
            donateGui = new MarketGuiController(this, donateRepository, new PlayerPointsPayment(playerPoints),
                    messages, guiLabels, categories, donateSync, true);
        }

        var command = Objects.requireNonNull(getCommand("ah"), "Команда ah отсутствует в plugin.yml");
        command.setExecutor(new MarketCommand(this, false));
        command.setTabCompleter(new MarketCommand(this, false));
        var donateCommand = Objects.requireNonNull(getCommand("dah"), "Command dah is missing from plugin.yml");
        donateCommand.setExecutor(new MarketCommand(this, true));
        donateCommand.setTabCompleter(new MarketCommand(this, true));
        getServer().getPluginManager().registerEvents(new MarketInventoryListener(this), this);

        setupUpdateChecker();
        new Metrics(this, BSTATS_PLUGIN_ID);
        PluginBanner.enabled(this, SUPPORT_DISCORD);
    }

    @Override
    public void onDisable() {
        if (gui != null) gui.shutdown();
        if (donateGui != null) donateGui.shutdown();
        if (sync != null) sync.cancel();
        if (donateSync != null) donateSync.cancel();
        if (updateChecker != null) updateChecker.cancel();
        if (repository != null) repository.close();
        if (donateRepository != null) donateRepository.close();
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
        if (gui != null) gui.shutdown();
        if (donateGui != null) donateGui.shutdown();
        if (sync != null) sync.cancel();
        if (donateSync != null) donateSync.cancel();
        sync = new MarketSync(this, repository);
        gui = new MarketGuiController(this, repository, new VaultPayment(economy), messages, guiLabels, categories, sync, false);
        if (donateRepository != null && playerPoints != null) {
            donateSync = new MarketSync(this, donateRepository);
            donateGui = new MarketGuiController(this, donateRepository, new PlayerPointsPayment(playerPoints),
                    messages, guiLabels, categories, donateSync, true);
        }
        if (updateChecker != null) updateChecker.cancel();
        setupUpdateChecker();
        sync.refreshAsync();
    }

    public List<MarketListing> activeListings(boolean donate) {
        MarketGuiController controller = donate ? donateGui : gui;
        return controller == null ? List.of() : controller.activeListings();
    }

    public void openAuction(Player player) {
        gui.openAuction(player);
    }

    public void openAuction(Player player, boolean donate) {
        if (!donate) {
            openAuction(player);
            return;
        }
        if (donateGui == null) {
            player.sendMessage("§cДонат-аукцион недоступен: PlayerPoints не установлен.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        donateGui.openAuction(player);
    }

    public void openAuctionSearch(Player player, String query) {
        gui.openAuctionSearch(player, query);
    }

    public void openAuctionSearch(Player player, String query, boolean donate) {
        if (donate) {
            if (donateGui != null) donateGui.openAuctionSearch(player, query);
            else openAuction(player, true);
            return;
        }
        openAuctionSearch(player, query);
    }

    public void openSellerGui(Player player, UUID sellerId) {
        gui.openSellerGui(player, sellerId);
    }

    public void openSellerGui(Player player, UUID sellerId, boolean donate) {
        if (donate) {
            if (donateGui != null) donateGui.openSellerGui(player, sellerId);
            else openAuction(player, true);
            return;
        }
        openSellerGui(player, sellerId);
    }

    public void renderAllViews() {
        if (gui != null) gui.renderAllViews();
        if (donateGui != null) donateGui.renderAllViews();
    }

    public void removeViewer(UUID viewerId) {
        if (gui != null) gui.removeViewer(viewerId);
        if (donateGui != null) donateGui.removeViewer(viewerId);
    }

    public void notifyUpdate(Player player) {
        if (updateChecker != null) updateChecker.notifyAdminOnJoin(player);
    }

    public String formatPrice(boolean donate, double amount, String formattedAmount) {
        String key = donate ? "prefix-playerpoints" : "prefix-vault";
        String fallback = donate ? "&d{price} PP" : "&a{price}⛃";
        String template = getConfig().getString(key, fallback);
        return ColorUtil.colorize(template.replace("{price}", formattedAmount));
    }

    private void setupUpdateChecker() {
        updateChecker = new UpdateChecker(this, new UpdateSettings(
                true, GITHUB_REPOSITORY, "pnmarket.admin", 6L, SUPPORT_DISCORD
        ));
        updateChecker.start();
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
            player.sendMessage(messages.message("error.price-too-low", Map.of("price",
                    formatPrice(false, minimumPrice, moneyFormat.format(minimumPrice)))));
            return;
        }
        if (maximumPrice > 0.0 && totalPrice > maximumPrice) {
            player.sendMessage(messages.message("error.price-too-high", Map.of("price",
                    formatPrice(false, maximumPrice, moneyFormat.format(maximumPrice)))));
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
                .append(component(formatPrice(false, totalPrice, moneyFormat.format(totalPrice)))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
    }

    public void sellPoints(Player player, String rawPrice) {
        if (donateRepository == null || donateSync == null || playerPoints == null) {
            player.sendMessage("§cДонат-аукцион недоступен: PlayerPoints не установлен.");
            return;
        }
        int totalPrice;
        try {
            totalPrice = Integer.parseInt(rawPrice);
        } catch (NumberFormatException exception) {
            reject(player, "error.invalid-price");
            return;
        }
        if (totalPrice <= 0) {
            reject(player, "error.price-positive");
            return;
        }
        int minimumPrice = Math.max(1, getConfig().getInt("donate-listing-price.minimum",
                getConfig().getInt("listing-price.minimum", 1)));
        int maximumPrice = Math.max(0, getConfig().getInt("donate-listing-price.maximum",
                getConfig().getInt("listing-price.maximum", 0)));
        if (totalPrice < minimumPrice) {
            player.sendMessage(messages.message("error.price-too-low", Map.of("price",
                    formatPrice(true, minimumPrice, moneyFormat.format(minimumPrice)))));
            return;
        }
        if (maximumPrice > 0 && totalPrice > maximumPrice) {
            player.sendMessage(messages.message("error.price-too-high", Map.of("price",
                    formatPrice(true, maximumPrice, moneyFormat.format(maximumPrice)))));
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
        if (totalPrice % amount != 0) {
            player.sendMessage("§cЦена за стак должна делиться на количество предметов без остатка.");
            return;
        }
        int limit = listingLimit(player);
        if (donateRepository.countActiveListings(player.getUniqueId()) >= limit) {
            player.sendMessage(messages.message("error.listing-limit", Map.of("limit", limit)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        ItemStack storedItem = hand.clone();
        try {
            MarketListing listing = donateRepository.create(player.getUniqueId(), storedItem, totalPrice / amount,
                    amount, System.currentTimeMillis());
            donateSync.listingCreated(listing);
        } catch (IOException | RuntimeException exception) {
            getLogger().warning("Не удалось создать лот донат-аукциона: " + exception.getMessage());
            reject(player, "error.serialization");
            return;
        }
        player.getInventory().setItemInMainHand(null);
        Component itemName = ItemLocalization.getNameComponent(storedItem);
        player.sendMessage(component(messages.message("notification.listed-prefix"))
                .append(itemName.color(NamedTextColor.YELLOW))
                .append(component(messages.message("notification.price-separator")))
                .append(component(formatPrice(true, totalPrice, moneyFormat.format(totalPrice)))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
    }

    /** Creates one auction lot from all non-empty main-inventory slots. */
    public void sellKit(Player player, String rawPrice, boolean donate) {
        MarketStorage targetRepository = donate ? donateRepository : repository;
        MarketSync targetSync = donate ? donateSync : sync;
        if (donate && (targetRepository == null || targetSync == null || playerPoints == null)) {
            player.sendMessage("§cДонат-аукцион недоступен.");
            return;
        }

        double totalPrice;
        try {
            totalPrice = donate ? Integer.parseInt(rawPrice) : Double.parseDouble(rawPrice.replace(',', '.'));
        } catch (NumberFormatException exception) {
            reject(player, "error.invalid-price");
            return;
        }
        if (!Double.isFinite(totalPrice) || totalPrice <= 0) {
            reject(player, "error.price-positive");
            return;
        }

        double minimumPrice = donate
                ? Math.max(1, getConfig().getInt("donate-listing-price.minimum",
                getConfig().getInt("listing-price.minimum", 1)))
                : Math.max(0.0, getConfig().getDouble("listing-price.minimum", 1.0));
        double maximumPrice = donate
                ? Math.max(0, getConfig().getInt("donate-listing-price.maximum",
                getConfig().getInt("listing-price.maximum", 0)))
                : getConfig().getDouble("listing-price.maximum", 0.0);
        if (totalPrice < minimumPrice) {
            player.sendMessage(messages.message("error.price-too-low", Map.of("price",
                    formatPrice(donate, minimumPrice, moneyFormat.format(minimumPrice)))));
            return;
        }
        if (maximumPrice > 0 && totalPrice > maximumPrice) {
            player.sendMessage(messages.message("error.price-too-high", Map.of("price",
                    formatPrice(donate, maximumPrice, moneyFormat.format(maximumPrice)))));
            return;
        }

        int limit = listingLimit(player);
        if (targetRepository.countActiveListings(player.getUniqueId()) >= limit) {
            player.sendMessage(messages.message("error.listing-limit", Map.of("limit", limit)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }

        ItemStack[] storage = player.getInventory().getStorageContents();
        List<ItemStack> contents = new java.util.ArrayList<>();
        for (ItemStack item : storage) {
            if (item == null || item.getType().isAir()) continue;
            if (MarketBundle.isBundle(this, item)) {
                player.sendMessage("§cНельзя вложить аукционный набор в другой набор.");
                return;
            }
            contents.add(item.clone());
        }
        if (contents.isEmpty()) {
            player.sendMessage("§cПоложите предметы набора в основной инвентарь.");
            return;
        }

        int maxSlots = Math.max(1, Math.min(36, getConfig().getInt("kits.max-slots", 18)));
        if (contents.size() > maxSlots) {
            player.sendMessage("§cВ наборе может быть максимум §e" + maxSlots + "§c заполненных слотов.");
            return;
        }

        ItemStack bundle;
        try {
            bundle = MarketBundle.create(this, contents);
        } catch (RuntimeException exception) {
            getLogger().warning("Не удалось подготовить набор: " + exception.getMessage());
            reject(player, "error.serialization");
            return;
        }

        ItemStack[] originalStorage = new ItemStack[storage.length];
        for (int index = 0; index < storage.length; index++) {
            originalStorage[index] = storage[index] == null ? null : storage[index].clone();
        }
        player.getInventory().setStorageContents(new ItemStack[storage.length]);
        try {
            MarketListing listing = targetRepository.create(player.getUniqueId(), bundle, totalPrice,
                    1, System.currentTimeMillis());
            targetSync.listingCreated(listing);
        } catch (IOException | RuntimeException exception) {
            player.getInventory().setStorageContents(originalStorage);
            getLogger().warning("Не удалось создать лот-набор: " + exception.getMessage());
            reject(player, "error.serialization");
            return;
        }

        player.sendMessage(component(messages.message("notification.listed-prefix"))
                .append(Component.text("Набор", NamedTextColor.YELLOW))
                .append(component(messages.message("notification.price-separator")))
                .append(component(formatPrice(donate, totalPrice, moneyFormat.format(totalPrice)))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> registration =
                getServer().getServicesManager().getRegistration(Economy.class);
        economy = registration == null ? null : registration.getProvider();
        return economy != null;
    }

    private void setupPlayerPoints() {
        if (!(getServer().getPluginManager().getPlugin("PlayerPoints") instanceof PlayerPoints points)) {
            getLogger().warning("PlayerPoints не найден: донат-аукцион /dah отключён.");
            return;
        }
        playerPoints = points.getAPI();
        if (playerPoints == null) getLogger().warning("PlayerPoints API недоступен: донат-аукцион /dah отключён.");
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

    private boolean setupDonateRepository() {
        try {
            FileConfiguration config = getConfig();
            String type = config.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
            donateRepository = switch (type) {
                case "sqlite" -> createSqliteRepository(config, true);
                case "mysql" -> createMySqlRepository(config, true);
                case "mongodb", "mongo" -> createMongoRepository(config, true);
                default -> throw new IllegalArgumentException("Неизвестный тип хранилища: " + type);
            };
            return true;
        } catch (RuntimeException exception) {
            getLogger().warning("Не удалось открыть хранилище донат-аукциона: " + exception.getMessage());
            return false;
        }
    }

    private MarketStorage createSqliteRepository(FileConfiguration config) {
        return createSqliteRepository(config, false);
    }

    private MarketStorage createSqliteRepository(FileConfiguration config, boolean donate) {
        String fileName = config.getString("storage.sqlite.file", "market.db");
        File databaseFile = new File(getDataFolder(), fileName);
        return new JdbcMarketRepository("org.sqlite.JDBC", "jdbc:sqlite:" + databaseFile.getAbsolutePath(),
                null, null, EXPIRY_MILLIS, getLogger(), donate ? "pnmarket_donate_listings" : "pnmarket_listings");
    }

    private MarketStorage createMySqlRepository(FileConfiguration config) {
        return createMySqlRepository(config, false);
    }

    private MarketStorage createMySqlRepository(FileConfiguration config, boolean donate) {
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
                config.getString("storage.mysql.password", ""), EXPIRY_MILLIS, getLogger(),
                donate ? "pnmarket_donate_listings" : "pnmarket_listings");
    }

    private MarketStorage createMongoRepository(FileConfiguration config) {
        return createMongoRepository(config, false);
    }

    private MarketStorage createMongoRepository(FileConfiguration config, boolean donate) {
        String uri = System.getenv("PNMARKET_MONGO_URI");
        if (uri == null || uri.isBlank()) uri = config.getString("storage.mongo.uri", "mongodb://localhost:27017");
        String collection = config.getString("storage.mongo.collection", "auction");
        return new MarketRepository(uri, config.getString("storage.mongo.database", "minecraft"),
                donate ? collection + "_donate" : collection, EXPIRY_MILLIS, getLogger());
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
