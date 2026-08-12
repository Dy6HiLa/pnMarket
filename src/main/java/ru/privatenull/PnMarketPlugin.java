package ru.privatenull;

import org.bstats.bukkit.Metrics;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.command.*;
import ru.privatenull.config.*;
import ru.privatenull.gui.*;
import ru.privatenull.gui.machine.MarketMachineService;
import ru.privatenull.localization.*;
import ru.privatenull.market.*;
import ru.privatenull.model.*;
import ru.privatenull.notification.*;
import ru.privatenull.pnlibrary.compat.*;
import ru.privatenull.pnlibrary.lifecycle.*;
import ru.privatenull.pnlibrary.text.*;
import ru.privatenull.pnlibrary.update.*;
import ru.privatenull.service.*;
import ru.privatenull.util.*;

import java.util.*;

public final class PnMarketPlugin extends JavaPlugin {
    private MessagesConfig messages;
    private GuiConfig guiConfig;
    private MarketRuntime runtime;
    private ListingService listings;
    private PriceFormatter prices;
    private CommissionService commissions;
    private PendingNotificationService notifications;
    private UpdateChecker updateChecker;
    private MarketMachineService machine;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessagesConfig(this);
        guiConfig = new GuiConfig(this);
        commissions = new CommissionService(this);
        LangRu.init(this);
        if (!supportedServer() || !startRuntime()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        notifications = new PendingNotificationService(this);
        prices = new PriceFormatter(this);
        ListingPolicy policy = new ListingPolicy(this, runtime.currencies());
        listings = new ListingService(this, runtime, policy, messages);

        registerCommands();
        getServer().getPluginManager().registerEvents(new MarketInventoryListener(this), this);
        machine = new MarketMachineService(this);
        getServer().getPluginManager().registerEvents(machine, this);
        startUpdateChecker();
        new Metrics(this, 32716);
        PluginBanner.enabled(this);
    }

    @Override
    public void onDisable() {
        if (machine != null) machine.shutdown();
        if (runtime != null) runtime.close();
        if (updateChecker != null) updateChecker.cancel();
        PluginBanner.disabled(this);
    }

    public void reloadRuntime() {
        reloadConfig();
        messages.reload();
        guiConfig.reload();
        LangRu.init(this);
        runtime.reload();
        if (updateChecker != null) updateChecker.cancel();
        startUpdateChecker();
    }

    public void reloadGuiRuntime() {
        guiConfig.reload();
        runtime.reload();
    }

    public MessagesConfig messages() {
        return messages;
    }

    public GuiConfig guiConfig() {
        return guiConfig;
    }

    public void openMachine(Player player) {
        machine.open(player);
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public String getSupportDiscord() {
        return PluginBanner.supportUrl();
    }

    public FavoriteService favorites() {
        return runtime.favorites();
    }

    public MarketSync marketSync() {
        return runtime.sync(false);
    }

    public MarketGuiController gui() {
        return runtime.gui(false);
    }

    public List<MarketListing> activeListings(boolean donate) {
        return runtime.activeListings(donate);
    }

    public void openAuction(Player player) {
        runtime.openAuction(player, false);
    }

    public void openAuction(Player player, boolean donate) {
        runtime.openAuction(player, donate);
    }

    public void openAuctionSearch(Player player, String query) {
        runtime.openSearch(player, query, false);
    }

    public void openAuctionSearch(Player player, String query, boolean donate) {
        runtime.openSearch(player, query, donate);
    }

    public void openSellerGui(Player player, UUID seller, boolean donate) {
        runtime.openSeller(player, seller, donate);
    }

    public void openSellerGui(Player player, UUID seller) {
        runtime.openSeller(player, seller, false);
    }

    public void openFavorites(Player player, boolean donate) {
        runtime.openFavorites(player, donate);
    }

    public void openNotificationCatalog(Player player, boolean donate) {
        runtime.openNotificationCatalog(player, donate);
    }

    public void renderAllViews() {
        runtime.renderAll();
    }

    public void removeViewer(UUID viewer) {
        runtime.removeViewer(viewer);
    }

    public void sell(Player player, String price) {
        listings.sell(player, price, false);
    }

    public void sellPoints(Player player, String price) {
        listings.sell(player, price, true);
    }

    public void sellAuto(Player player, boolean donate) {
        listings.sellAuto(player, donate);
    }

    public void sellKit(Player player, String price, boolean donate) {
        listings.sellKit(player, price, donate, "Набор");
    }

    public void sellKit(Player player, String price, boolean donate, String name) {
        listings.sellKit(player, price, donate, name);
    }

    public boolean confirmKitListing(Player player, boolean donate, String name, double price,
                                     Map<Integer, ItemStack> source) {
        return listings.confirmKit(player, donate, name, price, source);
    }

    public void notifyOnJoin(Player player) {
        if (updateChecker != null) updateChecker.notifyAdminOnJoin(player);
        if (notifications != null) notifications.deliver(player);
    }

    public void queueNotification(UUID playerId, String message) {
        Player online = getServer().getPlayer(playerId);
        if (online != null && online.isOnline()) online.sendMessage(message);
        else if (notifications != null) notifications.queue(playerId, message);
    }

    public void notifySellerSale(Player buyer, MarketListing listing, double price, boolean donate) {
        OfflinePlayer seller = getServer().getOfflinePlayer(listing.sellerId());
        double commission = commissions.sale(seller, runtime.payment(donate), price);
        String message = commission > 0
                ? "notification.seller-sale-with-commission" : "notification.seller-sale";
        queueNotification(listing.sellerId(), messages.message(message, Map.of(
                "buyer", buyer.getName(),
                "item", ItemLocalization.getPlainName(listing.item()),
                "price", formatPrice(donate, price, null),
                "commission", formatPrice(donate, commission, null),
                "received", formatPrice(donate, Math.max(0, price - commission), null))));
    }

    public String formatPrice(boolean donate, double amount, String formatted) {
        return prices.format(amount, donate);
    }

    public CommissionService commissions() {
        return commissions;
    }

    public String commissionGroup(OfflinePlayer player) {
        var permission = runtime == null ? null : runtime.currencies().permission();
        if (permission == null || player == null) return "default";
        try {
            String group = permission.getPrimaryGroup(null, player);
            return group == null || group.isBlank() ? "default" : group.toLowerCase(Locale.ROOT);
        } catch (RuntimeException exception) {
            return "default";
        }
    }

    public void relist(Player player, MarketListing listing, boolean donate) {
        listings.relist(player, listing, donate);
    }

    private boolean supportedServer() {
        ServerCapabilities.logEnvironment(this);
        ServerVersion version = ServerVersion.current();
        if (!version.isKnown() || !version.isBefore(1, 16, 5)) return true;
        getLogger().severe("pnMarket требует Minecraft 1.16.5 или новее; обнаружено " + version);
        return false;
    }

    private boolean startRuntime() {
        runtime = new MarketRuntime(this, messages, guiConfig, new GuiLabels(guiConfig));
        if (runtime.start()) return true;
        getLogger().severe("Не удалось инициализировать валюту или хранилище pnMarket.");
        return false;
    }

    private void registerCommands() {
        register("ah", false);
        register("dah", true);
        var command = Objects.requireNonNull(getCommand("pnmarket"),
                "Команда pnmarket отсутствует в plugin.yml");
        command.setExecutor(new PnMarketCommand(this));
        command.setTabCompleter(new PnMarketTabCompleter());
    }

    private void register(String name, boolean donate) {
        var command = Objects.requireNonNull(getCommand(name), "Команда " + name + " отсутствует в plugin.yml");
        var handler = new AuctionCommand(this, donate);
        command.setExecutor(handler);
        command.setTabCompleter(new AuctionTabCompleter(this, donate));
    }

    private void startUpdateChecker() {
        updateChecker = new UpdateChecker(this,
                new UpdateSettings(true, "Dy6HiLa/pnMarket", "pnmarket.admin", 6, PluginBanner.supportUrl()));
        updateChecker.start();
    }
}
