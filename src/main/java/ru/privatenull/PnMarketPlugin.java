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
import ru.privatenull.currency.CurrencyDefinition;
import ru.privatenull.currency.CurrencyRegistry;
import ru.privatenull.currency.CommandPayment;
import ru.privatenull.currency.MarketPayment;
import ru.privatenull.gui.MarketGuiController;
import ru.privatenull.gui.MarketInventoryListener;
import ru.privatenull.market.MarketBundle;
import ru.privatenull.market.FavoriteService;
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
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;

public final class PnMarketPlugin extends JavaPlugin {
    public static final String SUPPORT_DISCORD = "https://discord.gg/rRbzq6cnc6";
    private static final String GITHUB_REPOSITORY = "Dy6HiLa/pnMarket";
    private static final int BSTATS_PLUGIN_ID = 32716;
    private PendingSaleNotifier saleNotifier;

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
    private FavoriteService favoriteService;
    private UpdateChecker updateChecker;
    private final Set<String> pendingListings = ConcurrentHashMap.newKeySet();
    private Map<String, CurrencyDefinition> currencies = Map.of();
    private final Map<UUID, String> selectedCurrencies = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessagesConfig(this);
        guiLabels = new GuiLabels(messages);
        LangRu.init(this);
        saleNotifier = new PendingSaleNotifier(this);
        try {
            currencies = CurrencyRegistry.load(this, getLogger());
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Конфигурация валют невалидна: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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
        favoriteService = new FavoriteService(this);
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
        getServer().getPluginManager().registerEvents(saleNotifier, this);

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

    public Map<String, CurrencyDefinition> currencies() {
        return Collections.unmodifiableMap(currencies);
    }

    public String selectedCurrency(Player player) {
        return selectedCurrencies.getOrDefault(player.getUniqueId(), "vault");
    }

    public boolean selectCurrency(Player player, String id) {
        String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT);
        MarketPayment selected = currencyPayment(normalized);
        if (!currencies.containsKey(normalized) || selected == null || !selected.isAvailable()) return false;
        selectedCurrencies.put(player.getUniqueId(), normalized);
        return true;
    }

    public MarketPayment currencyPayment(String id) {
        CurrencyDefinition definition = currencies.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
        if (definition == null) return null;
        if ("vault".equals(definition.id())) return new VaultPayment(economy);
        if ("playerpoints".equals(definition.id()) && playerPoints != null) {
            return new PlayerPointsPayment(playerPoints);
        }
        return new CommandPayment(this, definition);
    }

    public boolean supportsListingCurrency(String id) {
        MarketPayment payment = currencyPayment(id);
        return payment != null && payment.isAvailable();
    }

    public void openAuction(Player player, String currencyId) {
        if (!selectCurrency(player, currencyId)) {
            player.sendMessage(messages.message("error.currency-unavailable"));
            return;
        }
        openAuction(player);
    }

    public MarketSync marketSync() {
        return sync;
    }

    public MarketGuiController gui() {
        return gui;
    }

    public FavoriteService favorites() {
        return favoriteService;
    }

    public void reloadRuntime() {
        reloadConfig();
        messages.reload();
        LangRu.init(this);
        try {
            currencies = CurrencyRegistry.load(this, getLogger());
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Конфигурация валют не перезагружена: " + exception.getMessage()
                    + ". Оставлены предыдущие безопасные настройки. Support: " + SUPPORT_DISCORD);
        }
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

    public void openFavorites(Player player, boolean donate) {
        MarketGuiController controller = donate ? donateGui : gui;
        if (controller == null) {
            openAuction(player, donate);
            return;
        }
        controller.openFavorites(player);
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

    public String formatPrice(String currencyId, double amount) {
        MarketPayment payment = currencyPayment(currencyId);
        if (payment == null || !payment.isAvailable()) return "";
        String configured = getConfig().getString("currencies." + currencyId + ".prefix", "&e{price} "
                + getConfig().getString("currencies." + currencyId + ".name", currencyId));
        return ColorUtil.colorize(configured.replace("{price}", payment.format(amount)));
    }

    private void setupUpdateChecker() {
        updateChecker = new UpdateChecker(this, new UpdateSettings(
                true, GITHUB_REPOSITORY, "pnmarket.admin", 6L, SUPPORT_DISCORD
        ));
        updateChecker.start();
    }

    public void sell(Player player, String rawPrice) {
        if (rawPrice.equalsIgnoreCase("auto")) {
            if (!player.hasPermission("pnmarket.sell.auto")) {
                reject(player, "command.no-permission");
                return;
            }
            rawPrice = autoPrice(player);
            if (rawPrice == null) {
                player.sendMessage("§cНе удалось определить цену: нет похожих лотов.");
                return;
            }
        }
        String currencyId = selectedCurrency(player);
        MarketPayment selectedPayment = currencyPayment(currencyId);
        if (selectedPayment == null || !selectedPayment.isAvailable()) {
            player.sendMessage(messages.message("error.currency-unavailable"));
            return;
        }
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
                    formatPrice(currencyId, minimumPrice))));
            return;
        }
        if (maximumPrice > 0.0 && totalPrice > maximumPrice) {
            player.sendMessage(messages.message("error.price-too-high", Map.of("price",
                    formatPrice(currencyId, maximumPrice))));
            return;
        }
        int limit = listingLimit(player);
        if (!beginListing(player, false, limit, sync)) {
            player.sendMessage(messages.message("error.listing-limit", Map.of("limit", limit)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            endListing(player.getUniqueId(), false);
            reject(player, "error.item-required");
            return;
        }
        int amount = hand.getAmount();
        if (amount <= 0) {
            endListing(player.getUniqueId(), false);
            reject(player, "error.invalid-amount");
            return;
        }
        ItemStack storedItem = hand.clone();
        player.getInventory().setItemInMainHand(null);
        UUID playerId = player.getUniqueId();
        runStorageAsync(() -> repository.create(playerId, storedItem, currencyId, totalPrice / amount,
                        amount, System.currentTimeMillis()), listing -> {
            endListing(playerId, false);
            sync.listingCreated(listing);
            favoriteService.notifyListing(listing, false);
            Component itemName = ItemLocalization.getNameComponent(storedItem);
            player.sendMessage(component(messages.message("notification.listed-prefix"))
                    .append(itemName.color(NamedTextColor.YELLOW))
                    .append(component(messages.message("notification.price-separator")))
                    .append(component(formatPrice(currencyId, totalPrice))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
        }, exception -> {
            endListing(playerId, false);
            restoreItems(player, List.of(storedItem));
            getLogger().warning("Не удалось создать лот: " + exception.getMessage());
            reject(player, "error.serialization");
        });
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
        if (!beginListing(player, true, limit, donateSync)) {
            player.sendMessage(messages.message("error.listing-limit", Map.of("limit", limit)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        ItemStack storedItem = hand.clone();
        player.getInventory().setItemInMainHand(null);
        UUID playerId = player.getUniqueId();
        runStorageAsync(() -> donateRepository.create(playerId, storedItem, "playerpoints", totalPrice / amount,
                        amount, System.currentTimeMillis()), listing -> {
            endListing(playerId, true);
            donateSync.listingCreated(listing);
            favoriteService.notifyListing(listing, true);
            Component itemName = ItemLocalization.getNameComponent(storedItem);
            player.sendMessage(component(messages.message("notification.listed-prefix"))
                    .append(itemName.color(NamedTextColor.YELLOW))
                    .append(component(messages.message("notification.price-separator")))
                    .append(component(formatPrice(true, totalPrice, moneyFormat.format(totalPrice)))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
        }, exception -> {
            endListing(playerId, true);
            restoreItems(player, List.of(storedItem));
            getLogger().warning("Не удалось создать лот донат-аукциона: " + exception.getMessage());
            reject(player, "error.serialization");
        });
    }

    /** Validates a kit and opens a read-only confirmation menu before any item is removed. */
    public void sellKit(Player player, String rawPrice, boolean donate) {
        sellKit(player, rawPrice, donate, "Набор");
    }

    public void sellKit(Player player, String rawPrice, boolean donate, String rawName) {
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
        if (targetSync.activeCount(player.getUniqueId()) >= limit) {
            player.sendMessage(messages.message("error.listing-limit", Map.of("limit", limit)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }

        ItemStack[] storage = player.getInventory().getStorageContents();
        Map<Integer, ItemStack> sourceSlots = new java.util.LinkedHashMap<>();
        List<String> blockedMaterials = getConfig().getStringList("kits.blocked-materials").stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .toList();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (item == null || item.getType().isAir()) continue;
            if (MarketBundle.isBundle(this, item)) {
                player.sendMessage("§cНельзя вложить аукционный набор в другой набор.");
                return;
            }
            if (blockedMaterials.contains(item.getType().name())) {
                player.sendMessage("§cПредмет §e" + ItemLocalization.getPlainName(item)
                        + " §cзапрещён внутри наборов.");
                return;
            }
            sourceSlots.put(slot, item.clone());
        }
        if (sourceSlots.isEmpty()) {
            player.sendMessage("§cПоложите предметы набора в основной инвентарь.");
            return;
        }

        int maxSlots = kitSlotLimit(player);
        if (sourceSlots.size() > maxSlots) {
            player.sendMessage("§cВ наборе может быть максимум §e" + maxSlots + "§c заполненных слотов.");
            return;
        }

        List<ItemStack> contents = sourceSlots.values().stream().map(ItemStack::clone).toList();
        int serializedSize;
        try {
            serializedSize = MarketBundle.serializedSize(contents);
        } catch (RuntimeException exception) {
            getLogger().warning("Не удалось подготовить набор: " + exception.getMessage());
            reject(player, "error.serialization");
            return;
        }
        int maximumBytes = Math.max(4096, getConfig().getInt("kits.max-serialized-bytes", 131072));
        if (serializedSize > maximumBytes) {
            player.sendMessage("§cНабор содержит слишком много данных: §e" + serializedSize
                    + "§c/§e" + maximumBytes + " §cбайт.");
            return;
        }

        MarketGuiController controller = donate ? donateGui : gui;
        if (controller == null) {
            player.sendMessage("§cАукцион сейчас недоступен.");
            return;
        }
        controller.openBundleCreatePreview(player, totalPrice, sanitizeKitName(rawName), sourceSlots, serializedSize);
    }

    public boolean confirmKitListing(Player player, boolean donate, String name, double totalPrice,
                                     Map<Integer, ItemStack> sourceSlots) {
        MarketStorage targetRepository = donate ? donateRepository : repository;
        MarketSync targetSync = donate ? donateSync : sync;
        if (targetRepository == null || targetSync == null) return false;
        int limit = listingLimit(player);
        if (!beginListing(player, donate, limit, targetSync)) {
            player.sendMessage(messages.message("error.listing-limit", Map.of("limit", limit)));
            return false;
        }

        ItemStack[] storage = player.getInventory().getStorageContents();
        for (Map.Entry<Integer, ItemStack> entry : sourceSlots.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= storage.length || storage[slot] == null
                    || !storage[slot].equals(entry.getValue())) {
                endListing(player.getUniqueId(), donate);
                player.sendMessage("§cСодержимое инвентаря изменилось. Создайте предпросмотр набора заново.");
                return false;
            }
        }

        List<ItemStack> contents = sourceSlots.values().stream().map(ItemStack::clone).toList();
        int maxSlots = kitSlotLimit(player);
        if (contents.isEmpty() || contents.size() > maxSlots) {
            endListing(player.getUniqueId(), donate);
            player.sendMessage("§cВ наборе может быть максимум §e" + maxSlots + "§c заполненных слотов.");
            return false;
        }
        List<String> blockedMaterials = getConfig().getStringList("kits.blocked-materials").stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .toList();
        for (ItemStack item : contents) {
            if (MarketBundle.isBundle(this, item) || blockedMaterials.contains(item.getType().name())) {
                endListing(player.getUniqueId(), donate);
                player.sendMessage("§cСодержимое набора больше не проходит проверку.");
                return false;
            }
        }
        int maximumBytes = Math.max(4096, getConfig().getInt("kits.max-serialized-bytes", 131072));
        try {
            if (MarketBundle.serializedSize(contents) > maximumBytes) {
                endListing(player.getUniqueId(), donate);
                player.sendMessage("§cНабор превышает допустимый объём данных.");
                return false;
            }
        } catch (RuntimeException exception) {
            endListing(player.getUniqueId(), donate);
            reject(player, "error.serialization");
            return false;
        }
        ItemStack bundle;
        try {
            bundle = MarketBundle.create(this, contents, name);
        } catch (RuntimeException exception) {
            endListing(player.getUniqueId(), donate);
            reject(player, "error.serialization");
            return false;
        }

        for (Integer slot : sourceSlots.keySet()) storage[slot] = null;
        player.getInventory().setStorageContents(storage);
        UUID playerId = player.getUniqueId();
        String currencyId = donate ? "playerpoints" : selectedCurrency(player);
        runStorageAsync(() -> targetRepository.create(playerId, bundle, currencyId, totalPrice,
                        1, System.currentTimeMillis()), listing -> {
            endListing(playerId, donate);
            targetSync.listingCreated(listing);
            favoriteService.notifyListing(listing, donate);
            player.sendMessage(component(messages.message("notification.listed-prefix"))
                    .append(Component.text(name, NamedTextColor.YELLOW))
                    .append(component(messages.message("notification.price-separator")))
                    .append(component(formatPrice(donate, totalPrice, moneyFormat.format(totalPrice)))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
            openAuction(player, donate);
        }, exception -> {
            endListing(playerId, donate);
            restoreItems(player, contents);
            getLogger().warning("Не удалось создать лот-набор: " + exception.getMessage());
            reject(player, "error.serialization");
        });
        return true;
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
                null, null, expiryMillis(), getLogger(), donate ? "pnmarket_donate_listings" : "pnmarket_listings");
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
                config.getString("storage.mysql.password", ""), expiryMillis(), getLogger(),
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
                donate ? collection + "_donate" : collection, expiryMillis(), getLogger());
    }

    private long expiryMillis() {
        double hours = getConfig().getDouble("listing-expiry.hours", 24.0);
        return (long) (Math.max(0.1, hours) * 60L * 60L * 1000L);
    }

    public long listingExpiryMillis() {
        return expiryMillis();
    }

    public void queueSaleNotification(UUID sellerId, String buyer, ItemStack item, double amount, String currencyId) {
        if (saleNotifier != null) saleNotifier.queue(sellerId, buyer, item, amount, currencyId);
    }

    private String autoPrice(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return null;
        List<MarketListing> matches = activeListings(false).stream()
                .filter(listing -> listing.item().isSimilar(hand))
                .toList();
        if (matches.isEmpty()) return null;
        double average = matches.stream().mapToDouble(MarketListing::pricePerUnit).average().orElse(0.0);
        double multiplier = getConfig().getDouble("auto-sell.price-multiplier", 1.0);
        return String.valueOf(Math.max(0.01, average * hand.getAmount() * multiplier));
    }

    private int listingLimit(Player player) {
        String group = primaryGroup(player);
        int fallback = getConfig().getInt("limits.default", 3);
        return Math.max(0, getConfig().getInt("limits." + group, fallback));
    }

    private int kitSlotLimit(Player player) {
        String group = primaryGroup(player);
        if (getConfig().isInt("kits.max-slots")) {
            return Math.max(1, Math.min(36, getConfig().getInt("kits.max-slots", 10)));
        }
        int fallback = getConfig().getInt("kits.max-slots.default", 10);
        return Math.max(1, Math.min(36, getConfig().getInt("kits.max-slots." + group, fallback)));
    }

    private String primaryGroup(Player player) {
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
        return group;
    }

    private boolean beginListing(Player player, boolean donate, int limit, MarketSync targetSync) {
        String key = player.getUniqueId() + ":" + donate;
        if (targetSync.activeCount(player.getUniqueId()) >= limit) return false;
        return pendingListings.add(key);
    }

    private void endListing(UUID playerId, boolean donate) {
        pendingListings.remove(playerId + ":" + donate);
    }

    private <T> void runStorageAsync(Callable<T> operation, Consumer<T> success,
                                     Consumer<Throwable> failure) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            T result;
            try {
                result = operation.call();
            } catch (Throwable throwable) {
                if (isEnabled()) getServer().getScheduler().runTask(this, () -> failure.accept(throwable));
                return;
            }
            if (isEnabled()) getServer().getScheduler().runTask(this, () -> success.accept(result));
        });
    }

    private void restoreItems(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) return;
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(
                items.stream().map(ItemStack::clone).toArray(ItemStack[]::new));
        overflow.values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private String sanitizeKitName(String rawName) {
        String value = rawName == null ? "" : org.bukkit.ChatColor.stripColor(rawName);
        if (value == null) value = "";
        value = value.replaceAll("[\\r\\n\\t]", " ").trim().replaceAll("\\s{2,}", " ");
        if (value.isEmpty()) value = "Набор";
        if (value.length() > 32) value = value.substring(0, 32).trim();
        return value;
    }

    private void reject(Player player, String messageKey) {
        player.sendMessage(messages.message(messageKey));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
    }

    private Component component(String value) {
        return LegacyComponentSerializer.legacySection().deserialize(value);
    }
}
