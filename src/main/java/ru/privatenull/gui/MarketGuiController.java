package ru.privatenull.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.localization.ItemLocalization;
import ru.privatenull.market.MarketFilter;
import ru.privatenull.market.MarketFilter.SortType;
import ru.privatenull.market.MarketCategories;
import ru.privatenull.market.MarketSearch;
import ru.privatenull.market.MarketSync;
import ru.privatenull.config.GuiLabels;
import ru.privatenull.config.MessagesConfig;
import ru.privatenull.model.MarketListing;
import ru.privatenull.model.PurchaseReservation;
import ru.privatenull.storage.MarketStorage;

import java.text.DecimalFormat;
import java.util.*;

public final class MarketGuiController {
    private static final long EXPIRY_MILLIS = 24L * 60L * 60L * 1000L;

    private static final int[] AUCTION_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int[] PURCHASE_BLACK_SLOTS = {
            0, 2, 3, 4, 5, 6, 8, 10, 16, 17, 18, 26, 27, 35, 37, 43, 45, 47, 48, 49, 50, 51, 53
    };
    private static final int[] PURCHASE_ORANGE_SLOTS = {
            1, 7, 9, 11, 12, 13, 14, 15, 19, 25, 28, 34, 36, 38, 39, 40, 41, 42, 44, 46, 52
    };
    private static final int[] AUCTION_BLACK_SLOTS = {
            0, 2, 3, 4, 5, 6, 8,
            18, 26, 27, 35, 45,
            47, 48, 49, 50, 51, 53
    };

    private static final int[] AUCTION_ORANGE_SLOTS = {
            1, 7, 9, 17, 36, 44, 46, 52
    };

    private static final int SLOT_MY_ITEMS = 45;
    private static final int SLOT_PREV_PAGE = 47;
    private static final int SLOT_NEXT_PAGE = 51;
    private static final int SLOT_SORT = 52;
    private static final int SLOT_CATEGORY = 53;

    private static final int SLOT_BACK_TOP = 21;
    private static final int SLOT_PREVIEW = 22;
    private static final int SLOT_BUY = 23;
    private static final int SLOT_SELLER_HEAD = 31;
    private static final int SLOT_MINUS_1 = 29;
    private static final int SLOT_MINUS_10 = 30;
    private static final int SLOT_PLUS_1 = 32;
    private static final int SLOT_PLUS_10 = 33;
    private static final int SLOT_BACK_BOTTOM = 49;

    private static final int[] SELLER_SLOTS = {
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33
    };

    private final PnMarketPlugin plugin;
    private final MarketStorage repository;
    private final Economy economy;
    private final MessagesConfig messages;
    private final GuiLabels guiLabels;
    private final MarketCategories categories;

    final Map<UUID, AuctionView> auctionViews = new HashMap<>();
    final Map<UUID, PurchaseView> purchaseViews = new HashMap<>();
    final Map<UUID, SellerView> sellerViews = new HashMap<>();
    final Map<UUID, MyItemsView> myItemsViews = new HashMap<>();
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0");

    public MarketGuiController(PnMarketPlugin plugin, MarketStorage repository, Economy economy,
                               MessagesConfig messages, GuiLabels guiLabels, MarketCategories categories) {
        this.plugin = plugin;
        this.repository = repository;
        this.economy = economy;
        this.messages = messages;
        this.guiLabels = guiLabels;
        this.categories = categories;
    }

    private MarketSync sync() {
        return plugin.marketSync();
    }

    public List<MarketListing> activeListings() {
        return sync().all().stream()
                .filter(listing -> listing.amount() > 0)
                .filter(listing -> "ACTIVE".equalsIgnoreCase(listing.status()))
                .toList();
    }

    private boolean hasActiveListings(UUID sellerId) {
        return sync().activeCount(sellerId) > 0;
    }

    private String formatMoney(double d) {
        return moneyFormat.format(d);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private Component component(String value) {
        return LegacyComponentSerializer.legacySection().deserialize(value);
    }

    private String formatTimeRemaining(long createdAt) {
        long left = createdAt + EXPIRY_MILLIS - System.currentTimeMillis();
        if (left <= 0) return messages.message("time.empty");
        long minutes = left / 60000L;
        long hours = minutes / 60;
        minutes %= 60;
        return messages.message("time.remaining", Map.of("hours", hours, "minutes", minutes));
    }

    private ItemStack createIcon(Material material, String name, String... loreLines) {
        ItemStack i = new ItemStack(material);
        ItemMeta meta = i.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines != null && loreLines.length > 0) {
                meta.setLore(Arrays.asList(loreLines));
            }
            i.setItemMeta(meta);
        }
        return hideAttributes(i);
    }

    private List<String> buildListingLore(MarketListing listing, int amountForLore) {
        List<String> lore = new ArrayList<>();
        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.sellerId());
        String ownerName = seller.getName() != null ? seller.getName() : listing.sellerId().toString();
        double totalPrice = listing.pricePerUnit() * amountForLore;
        String time = formatTimeRemaining(listing.createdAt());
        lore.add("");
        lore.add(messages.message("listing.info-title"));
        lore.add(messages.message("listing.seller", Map.of("seller", ownerName)));
        lore.add(messages.message("listing.price", Map.of("price", formatMoney(totalPrice))));
        if (amountForLore > 1) {
            lore.add(messages.message("listing.amount", Map.of("amount", amountForLore)));
        }
        lore.add(messages.message("listing.expires", Map.of("time", time)));
        lore.add("");
        return lore;
    }

    public void openAuction(Player player) {
        openAuction(player, MarketCategories.ALL, SortType.NEW_FIRST, null, 0, false);
    }

    public void openAuctionSearch(Player player, String query) {
        openAuction(player, MarketCategories.ALL, SortType.NEW_FIRST, query, 0, true);
    }

    private void openAuction(Player player, String category, SortType sort, String searchQuery, int page, boolean isSearch) {
        UUID uuid = player.getUniqueId();
        AuctionView view = new AuctionView();
        view.viewer = uuid;
        view.category = category;
        view.sort = sort;
        view.searchQuery = searchQuery;
        view.page = page;
        view.isSearch = isSearch;
        view.slotToListingId = new HashMap<>();

        String baseTitle = (searchQuery != null && !searchQuery.isEmpty())
                ? searchQuery : messages.message("gui.title.auction");
        String title = messages.message("gui.title.auction-page", Map.of(
                "name", baseTitle, "page", page + 1
        ));

        Inventory inv = Bukkit.createInventory(view, 54, title);
        view.inventory = inv;
        auctionViews.put(uuid, view);

        decorateAuction(inv, isSearch);
        initFilterIcons(view);

        List<MarketListing> filtered = getFilteredListings(view);
        int pageSize = AUCTION_SLOTS.length;
        int totalPages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        if (view.page < 0) view.page = 0;
        if (view.page >= totalPages) view.page = totalPages - 1;

        int startIndex = view.page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, filtered.size());

        ItemStack blackFlag = new ItemStack(Material.BLACK_BANNER);
        ItemMeta meta = blackFlag.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.message("gui.action.loading"));
            blackFlag.setItemMeta(meta);
        }
        hideAttributes(blackFlag);

        int index = startIndex;
        for (int slot : AUCTION_SLOTS) {
            if (index >= endIndex) {
                inv.setItem(slot, null);
            } else {
                inv.setItem(slot, blackFlag);
                index++;
            }
        }

        player.openInventory(inv);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                if (!player.getOpenInventory().getTopInventory().equals(inv)) return;
                fillAuctionInventory(player, view);
            }
        }.runTaskLater(plugin, 3L);
    }

    private List<MarketListing> getFilteredListings(AuctionView view) {
        List<MarketListing> all = sync().all().stream()
                .filter(listing -> listing.amount() > 0)
                .filter(listing -> "ACTIVE".equalsIgnoreCase(listing.status()))
                .toList();

        List<MarketListing> filtered = new ArrayList<>(all);

        if (view.searchQuery != null && !view.searchQuery.isEmpty()) {
            String q = view.searchQuery.toLowerCase(Locale.ROOT);
            filtered.removeIf(l -> !MarketSearch.matches(l, q));
        }

        if (!view.isSearch && !MarketCategories.ALL.equals(view.category)) {
            filtered.removeIf(l -> !categories.categoryOf(l).equals(view.category));
        }

        MarketFilter.sortListings(filtered, view.sort);
        return filtered;
    }

    private void decorateAuction(Inventory inv, boolean isSearch) {
        ItemStack black = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bm = black.getItemMeta();
        if (bm != null) {
            bm.setDisplayName(" ");
            black.setItemMeta(bm);
        }
        hideAttributes(black);

        ItemStack orange = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta om = orange.getItemMeta();
        if (om != null) {
            om.setDisplayName(" ");
            orange.setItemMeta(om);
        }
        hideAttributes(orange);

        for (int slot : AUCTION_BLACK_SLOTS) {
            inv.setItem(slot, black);
        }
        for (int slot : AUCTION_ORANGE_SLOTS) {
            inv.setItem(slot, orange);
        }

        if (isSearch) {
            ItemStack back = createIcon(
                    Material.RED_CANDLE,
                    messages.message("gui.action.back"),
                    "",
                    messages.message("gui.search.title"),
                    messages.message("gui.search.line-1"),
                    messages.message("gui.search.line-2"),
                    ""
            );
            inv.setItem(SLOT_MY_ITEMS, back);
        } else {
            ItemStack myItems = createIcon(
                    Material.BARREL,
                    messages.message("gui.action.my-items"),
                    "",
                    messages.message("gui.my-items.title"),
                    messages.message("gui.my-items.line-1"),
                    messages.message("gui.my-items.line-2"),
                    messages.message("gui.my-items.line-3"),
                    "",
                    messages.message("gui.action.open")
            );
            inv.setItem(SLOT_MY_ITEMS, myItems);
        }
    }

    private void initFilterIcons(AuctionView view) {
        if (view.isSearch) {
            updateSortIcon(view);
            view.inventory.setItem(SLOT_CATEGORY, null);
            return;
        }

        updateCategoryIcon(view, categoryCounts(), activeListings().size());
        updateSortIcon(view);
    }

    void fillAuctionInventory(Player player, AuctionView view) {
        Inventory inv = view.inventory;
        view.slotToListingId.clear();

        List<MarketListing> filtered = getFilteredListings(view);

        int pageSize = AUCTION_SLOTS.length;
        int totalPages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);

        if (view.page < 0) view.page = 0;
        if (view.page >= totalPages) view.page = totalPages - 1;

        int startIndex = view.page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, filtered.size());

        ItemStack black = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bm = black.getItemMeta();
        if (bm != null) {
            bm.setDisplayName(" ");
            black.setItemMeta(bm);
        }
        hideAttributes(black);
        inv.setItem(SLOT_PREV_PAGE, black);
        inv.setItem(SLOT_NEXT_PAGE, black);

        if (view.page > 0) {
            ItemStack prev = createIcon(
                    Material.ARROW, messages.message("gui.action.previous-page")
            );
            inv.setItem(SLOT_PREV_PAGE, prev);
        }

        if (view.page < totalPages - 1) {
            ItemStack next = createIcon(
                    Material.ARROW, messages.message("gui.action.next-page")
            );
            inv.setItem(SLOT_NEXT_PAGE, next);
        }

        if (!view.isSearch) {
            updateCategoryIcon(view, categoryCounts(), activeListings().size());
        }
        updateSortIcon(view);

        int index = startIndex;
        for (int slot : AUCTION_SLOTS) {
            if (index >= endIndex) {
                inv.setItem(slot, null);
                continue;
            }
            MarketListing listing = filtered.get(index++);
            ItemStack display = listing.item().clone();
            int displayAmount = Math.min(listing.amount(), display.getMaxStackSize());
            if (displayAmount <= 0) displayAmount = 1;
            display.setAmount(displayAmount);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.addAll(buildListingLore(listing, listing.amount()));
                if (listing.sellerId().equals(player.getUniqueId())) {
                    lore.add(messages.message("gui.action.collect"));
                } else {
                    lore.add(messages.message("gui.action.purchase"));
                }
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                display.setItemMeta(meta);
            }
            hideAttributes(display);
            inv.setItem(slot, display);
            view.slotToListingId.put(slot, listing.id());
        }

        String baseTitle = (view.searchQuery != null && !view.searchQuery.isEmpty())
                ? view.searchQuery : messages.message("gui.title.auction");
        String newTitle = messages.message("gui.title.auction-pages", Map.of(
                "name", baseTitle, "page", view.page + 1, "pages", totalPages
        ));
        if (!player.getOpenInventory().getTitle().equals(newTitle)) {
            Inventory newInv = Bukkit.createInventory(view, 54, newTitle);
            newInv.setContents(inv.getContents());
            view.inventory = newInv;
            player.openInventory(newInv);
        }
    }

    private void updateCategoryIcon(AuctionView view, Map<String, Integer> counts, int allCount) {
        if (view.isSearch) {
            view.inventory.setItem(SLOT_CATEGORY, null);
            return;
        }
        Inventory inv = view.inventory;
        ItemStack item = new ItemStack(Material.MANGROVE_HANGING_SIGN);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.message("gui.filter.category-title"));
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(messages.message("gui.filter.current-category",
                    Map.of("category", categories.displayName(view.category))));
            lore.add("");
            lore.add(messages.message("gui.filter.categories-title"));
            for (String category : categories.ids()) {
                String label = categories.displayName(category);
                int count = MarketCategories.ALL.equals(category) ? allCount : counts.getOrDefault(category, 0);
                String prefix = category.equals(view.category) ? " §x§B§4§E§E§4§1» " : " §7- §f";
                lore.add(color(prefix + label + " §7(" + count + ")"));
            }
            lore.add("");
            lore.add(messages.message("gui.action.next"));
            lore.add(messages.message("gui.action.previous"));
            lore.add("");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        hideAttributes(item);
        inv.setItem(SLOT_CATEGORY, item);
    }

    private Map<String, Integer> categoryCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (MarketListing listing : activeListings()) {
            String category = categories.categoryOf(listing);
            counts.put(category, counts.getOrDefault(category, 0) + 1);
        }
        return counts;
    }

    private void updateSortIcon(AuctionView view) {
        Inventory inv = view.inventory;
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String currentName = guiLabels.sort(view.sort);
            meta.setDisplayName(messages.message("gui.filter.sort-title"));
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(messages.message("gui.filter.current-sort", Map.of("sort", currentName)));
            lore.add("");
            lore.add(messages.message("gui.filter.sorts-title"));
            List<SortType> types = new ArrayList<>(Arrays.asList(SortType.values()));
            types.sort(Comparator.comparingInt(this::getSortOrderPriority));
            for (SortType type : types) {
                String name = guiLabels.sort(type);
                String prefix = type == view.sort ? " §x§B§4§E§E§4§1» " : " §7- §f";
                lore.add(color(prefix + name));
            }
            lore.add("");
            lore.add(messages.message("gui.action.next"));
            lore.add(messages.message("gui.action.previous"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        hideAttributes(item);
        int sortSlot = view.isSearch ? 53 : SLOT_SORT;
        if (view.isSearch) {
            ItemStack black = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta blackMeta = black.getItemMeta();
            if (blackMeta != null) {
                blackMeta.setDisplayName(" ");
                black.setItemMeta(blackMeta);
            }
            hideAttributes(black);
            inv.setItem(52, black);
        }
        inv.setItem(sortSlot, item);
    }

    private int getSortOrderPriority(SortType type) {
        return switch (type) {
            case NEW_FIRST -> 0;
            case OLD_FIRST -> 1;
            case PRICE_UNIT_DESC -> 2;
            case PRICE_UNIT_ASC -> 3;
            case PRICE_TOTAL_DESC -> 4;
            case PRICE_TOTAL_ASC -> 5;
        };
    }

    private void updateSellerSortIcon(SellerView view) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.message("gui.filter.sort-title"));
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(messages.message("gui.filter.current-sort", Map.of("sort", guiLabels.sort(view.sort))));
            lore.add("");
            lore.add(messages.message("gui.filter.available-sorts"));
            List<SortType> types = new ArrayList<>(Arrays.asList(SortType.values()));
            types.sort(Comparator.comparingInt(this::getSortOrderPriority));
            for (SortType type : types) {
                String prefix = type == view.sort ? " §x§B§4§E§E§4§1» " : " §7- §f";
                lore.add(color(prefix + guiLabels.sort(type)));
            }
            lore.add("");
            lore.add(messages.message("gui.action.next"));
            lore.add(messages.message("gui.action.previous"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        hideAttributes(item);
        view.inventory.setItem(53, item);
    }

    void fillSellerInventory(SellerView view) {
        view.slotToListingId.clear();
        List<MarketListing> listings = new ArrayList<>(sync().bySeller(view.sellerId).stream()
                .filter(listing -> listing.amount() > 0)
                .filter(listing -> "ACTIVE".equalsIgnoreCase(listing.status()))
                .toList());
        MarketFilter.sortListings(listings, view.sort);
        int index = 0;
        for (int slot : SELLER_SLOTS) {
            if (index >= listings.size()) {
                view.inventory.setItem(slot, null);
                continue;
            }
            MarketListing listing = listings.get(index++);
            ItemStack display = listing.item().clone();
            display.setAmount(Math.max(1, Math.min(listing.amount(), display.getMaxStackSize())));
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>(buildListingLore(listing, listing.amount()));
                lore.add(messages.message("gui.action.purchase"));
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                display.setItemMeta(meta);
            }
            hideAttributes(display);
            view.inventory.setItem(slot, display);
            view.slotToListingId.put(slot, listing.id());
        }
        updateSellerSortIcon(view);
    }

    private void refreshAuctionForAll() {
        for (Map.Entry<UUID, AuctionView> entry : auctionViews.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            AuctionView view = entry.getValue();
            if (player != null && player.isOnline()
                    && player.getOpenInventory().getTopInventory().equals(view.inventory)) {
                fillAuctionInventory(player, view);
            }
        }
    }

    private void refreshSellerViewsForAll() {
        for (Map.Entry<UUID, SellerView> entry : sellerViews.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            SellerView view = entry.getValue();
            if (player != null && player.isOnline()
                    && player.getOpenInventory().getTopInventory().equals(view.inventory)) {
                fillSellerInventory(view);
            }
        }
    }

    private void refreshMyItemsForAll() {
        for (Map.Entry<UUID, MyItemsView> entry : myItemsViews.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            MyItemsView view = entry.getValue();
            if (player != null && player.isOnline()
                    && player.getOpenInventory().getTopInventory().equals(view.inventory)) {
                fillMyItemsInventory(entry.getKey(), view);
            }
        }
    }

    private void refreshPurchaseViewsForAll() {
        for (Map.Entry<UUID, PurchaseView> entry : new ArrayList<>(purchaseViews.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            PurchaseView view = entry.getValue();
            if (player == null || !player.isOnline()
                    || !player.getOpenInventory().getTopInventory().equals(view.inventory)) continue;
            MarketListing fresh = sync().byId(view.listing.id()).orElse(null);
            if (fresh == null || fresh.amount() <= 0 || !"ACTIVE".equalsIgnoreCase(fresh.status())) {
                purchaseViews.remove(entry.getKey());
                player.closeInventory();
                player.sendMessage(messages.message("error.listing-unavailable"));
                continue;
            }
            view.listing = fresh;
            view.maxAmount = fresh.amount();
            view.quantity = Math.max(1, Math.min(view.quantity, view.maxAmount));
            updateQuantityItems(view);
        }
    }

    private void refreshAllViews() {
        sync().refreshAsync();
    }

    public void renderAllViews() {
        refreshAuctionForAll();
        refreshSellerViewsForAll();
        refreshMyItemsForAll();
        refreshPurchaseViewsForAll();
    }

    private void openMyItems(Player player) {
        UUID viewerId = player.getUniqueId();
        MyItemsView view = new MyItemsView();
        view.inventory = Bukkit.createInventory(view, 54,
                ChatColor.DARK_GRAY + messages.message("gui.title.my-items"));
        view.slotToListingId = new HashMap<>();
        myItemsViews.put(viewerId, view);
        fillMyItemsInventory(viewerId, view);
        player.openInventory(view.inventory);
    }

    void fillMyItemsInventory(UUID viewerId, MyItemsView view) {
        view.slotToListingId = new HashMap<>();
        decoratePurchase(view.inventory);
        Iterator<MarketListing> listings = sync().bySeller(viewerId).stream()
                .filter(listing -> listing.amount() > 0)
                .filter(listing -> "EXPIRED".equalsIgnoreCase(listing.status())
                        || "RETURNED".equalsIgnoreCase(listing.status()))
                .iterator();
        for (int slot : SELLER_SLOTS) {
            if (!listings.hasNext()) {
                view.inventory.setItem(slot, null);
                continue;
            }
            MarketListing listing = listings.next();
            ItemStack display = listing.item().clone();
            display.setAmount(Math.max(1, Math.min(listing.amount(), display.getMaxStackSize())));
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>(buildListingLore(listing, listing.amount()));
                lore.add(messages.message("listing.status-title"));
                lore.add(messages.message("listing.status-amount", Map.of("amount", listing.amount())));
                String status = "EXPIRED".equalsIgnoreCase(listing.status())
                        ? messages.message("listing.status-expired")
                        : messages.message("listing.status-returned");
                lore.add(messages.message("listing.status-value", Map.of("status", status)));
                lore.add("");
                lore.add(messages.message("gui.action.collect"));
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                display.setItemMeta(meta);
            }
            hideAttributes(display);
            view.inventory.setItem(slot, display);
            view.slotToListingId.put(slot, listing.id());
        }
        view.inventory.setItem(SLOT_BACK_BOTTOM,
                createIcon(Material.RED_CANDLE, messages.message("gui.action.back")));
    }

    void handleMyItemsClick(Player player, MyItemsView view, int slot) {
        if (slot == SLOT_BACK_BOTTOM) {
            myItemsViews.remove(player.getUniqueId());
            openAuction(player);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
            return;
        }
        String id = view.slotToListingId.get(slot);
        if (id == null) return;
        MarketListing listing = loadListingById(id);
        if (listing == null || listing.amount() <= 0) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            view.inventory.setItem(slot, null);
            view.slotToListingId.remove(slot);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        ItemStack item = listing.item().clone();
        item.setAmount(listing.amount());
        hideAttributes(item);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(value -> player.getWorld().dropItemNaturally(player.getLocation(), value));
        repository.delete(listing.id());
        sync().listingRemoved(listing.id());
        Component itemName = ItemLocalization.getNameComponent(listing.item());
        player.sendMessage(component(messages.message("notification.collected-prefix"))
                .append(itemName.color(NamedTextColor.YELLOW)));
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.9f, 1.1f);
        view.inventory.setItem(slot, null);
        view.slotToListingId.remove(slot);
    }

    private void openPurchaseGui(Player player, MarketListing listing) {
        PurchaseView view = new PurchaseView();
        view.listing = listing;
        view.maxAmount = listing.amount();
        view.quantity = 1;
        view.sellerId = listing.sellerId();
        view.inventory = Bukkit.createInventory(view, 54,
                ChatColor.DARK_GRAY + messages.message("gui.title.purchase"));
        purchaseViews.put(player.getUniqueId(), view);
        decoratePurchase(view.inventory);
        view.inventory.setItem(SLOT_BACK_TOP,
                createIcon(Material.RED_CANDLE, messages.message("gui.action.back")));
        view.inventory.setItem(SLOT_MINUS_1, createIcon(Material.RED_WOOL, "§c-1"));
        view.inventory.setItem(SLOT_MINUS_10, createIcon(Material.RED_WOOL, "§c-10"));
        view.inventory.setItem(SLOT_PLUS_1, createIcon(Material.GREEN_WOOL, "§a+1"));
        view.inventory.setItem(SLOT_PLUS_10, createIcon(Material.GREEN_WOOL, "§a+10"));
        Inventory inv = view.inventory;
        PurchaseView pv = view;

        ItemStack buy = createIcon(
                Material.LIME_CANDLE, messages.message("gui.action.buy")
        );
        inv.setItem(SLOT_BUY, buy);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm != null) {
            OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.sellerId());
            sm.setOwningPlayer(seller);
            String sellerName = seller.getName() != null ? seller.getName() : listing.sellerId().toString();
            sm.setDisplayName(messages.message("gui.seller.label", Map.of("seller", sellerName)));
            List<String> hl = new ArrayList<>();
            hl.add("");
            hl.add(messages.message("gui.seller.item-title"));
            hl.add(messages.message("gui.seller.item-line-1"));
            hl.add(messages.message("gui.seller.item-line-2"));
            hl.add("");
            hl.add(messages.message("gui.seller.item-action"));
            sm.setLore(hl);
            sm.addItemFlags(
                    ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                    ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_ARMOR_TRIM,
                    ItemFlag.HIDE_DYE
            );
            head.setItemMeta(sm);
        }
        hideAttributes(head);
        inv.setItem(SLOT_SELLER_HEAD, head);

        updateQuantityItems(pv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
    }

    private void decoratePurchase(Inventory inv) {
        ItemStack black = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bm = black.getItemMeta();
        if (bm != null) {
            bm.setDisplayName(" ");
            black.setItemMeta(bm);
        }
        hideAttributes(black);
        ItemStack orange = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta om = orange.getItemMeta();
        if (om != null) {
            om.setDisplayName(" ");
            orange.setItemMeta(om);
        }
        hideAttributes(orange);
        for (int slot : PURCHASE_BLACK_SLOTS) {
            inv.setItem(slot, black);
        }
        for (int slot : PURCHASE_ORANGE_SLOTS) {
            inv.setItem(slot, orange);
        }
    }

    private void updateQuantityItems(PurchaseView pv) {
        Inventory inv = pv.inventory;
        MarketListing listing = pv.listing;
        ItemStack preview = listing.item().clone();
        int displayAmount = Math.min(pv.quantity, preview.getMaxStackSize());
        if (displayAmount <= 0) displayAmount = 1;
        preview.setAmount(displayAmount);
        ItemMeta pm = preview.getItemMeta();
        if (pm != null) {
            List<String> lore = new ArrayList<>();
            lore.addAll(buildListingLore(listing, pv.quantity));
            pm.setLore(lore);
            pm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            preview.setItemMeta(pm);
        }
        hideAttributes(preview);

        inv.setItem(SLOT_PREVIEW, preview);
        ItemStack buy = inv.getItem(SLOT_BUY);
    }

    void handlePurchaseClick(Player player, PurchaseView pv, int slot) {
        if (slot == SLOT_BACK_TOP) {
            purchaseViews.remove(player.getUniqueId());
            openAuction(player);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
            return;
        }
        if (slot == SLOT_SELLER_HEAD) {
            purchaseViews.remove(player.getUniqueId());
            openSellerGui(player, pv.sellerId);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
            return;
        }
        if (slot == SLOT_MINUS_1) {
            pv.quantity -= 1;
            if (pv.quantity < 1) pv.quantity = 1;
            updateQuantityItems(pv);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            return;
        }
        if (slot == SLOT_MINUS_10) {
            pv.quantity -= 10;
            if (pv.quantity < 1) pv.quantity = 1;
            updateQuantityItems(pv);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.9f);
            return;
        }
        if (slot == SLOT_PLUS_1) {
            pv.quantity += 1;
            if (pv.quantity > pv.maxAmount) pv.quantity = pv.maxAmount;
            updateQuantityItems(pv);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
            return;
        }
        if (slot == SLOT_PLUS_10) {
            pv.quantity += 10;
            if (pv.quantity > pv.maxAmount) pv.quantity = pv.maxAmount;
            updateQuantityItems(pv);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.15f);
            return;
        }
        if (slot == SLOT_BUY) {
            performPurchase(player, pv);
        }
    }

    private void performPurchase(Player player, PurchaseView pv) {
        MarketListing fresh = loadListingById(pv.listing.id());
        if (fresh == null || fresh.amount() <= 0) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            purchaseViews.remove(player.getUniqueId());
            player.closeInventory();
            refreshAllViews();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        if (!"ACTIVE".equalsIgnoreCase(fresh.status())) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            purchaseViews.remove(player.getUniqueId());
            player.closeInventory();
            refreshAllViews();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        if (fresh.sellerId().equals(player.getUniqueId())) {
            player.sendMessage(messages.message("error.own-listing"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        int requestedAmount = Math.min(pv.quantity, fresh.amount());
        if (requestedAmount <= 0) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            purchaseViews.remove(player.getUniqueId());
            player.closeInventory();
            refreshAllViews();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        double totalPrice = fresh.pricePerUnit() * requestedAmount;
        if (!economy.has(player, totalPrice)) {
            player.sendMessage(messages.message("error.insufficient-funds"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }

        PurchaseReservation reservation = repository.reserve(fresh.id(), requestedAmount).orElse(null);
        if (reservation == null) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            refreshAllViews();
            return;
        }
        fresh = reservation.listing();
        int toBuy = reservation.quantity();
        totalPrice = fresh.pricePerUnit() * toBuy;
        var withdrawal = economy.withdrawPlayer(player, totalPrice);
        if (!withdrawal.transactionSuccess()) {
            repository.rollbackReservation(fresh.id(), toBuy);
            player.sendMessage(messages.message("error.insufficient-funds"));
            refreshAllViews();
            return;
        }
        OfflinePlayer seller = Bukkit.getOfflinePlayer(fresh.sellerId());
        var deposit = economy.depositPlayer(seller, totalPrice);
        if (!deposit.transactionSuccess()) {
            economy.depositPlayer(player, totalPrice);
            repository.rollbackReservation(fresh.id(), toBuy);
            player.sendMessage(messages.message("error.purchase-failed"));
            refreshAllViews();
            return;
        }
        repository.finalizeReservation(reservation);
        if (reservation.remainingAmount() == 0) {
            sync().listingRemoved(fresh.id());
        } else {
            sync().listingUpdated(fresh.withAmount(reservation.remainingAmount()));
        }

        if (seller.isOnline() && seller.getPlayer() != null) {
            Player sp = seller.getPlayer();
            Component itemName = ItemLocalization.getNameComponent(fresh.item());
            Component msg = component(messages.message("notification.seller-sale-prefix",
                            Map.of("buyer", player.getName())))
                    .append(component(messages.message("notification.seller-sale-middle")))
                    .append(itemName.color(NamedTextColor.YELLOW))
                    .append(component(messages.message("notification.price-separator")))
                    .append(Component.text(formatMoney(totalPrice) + "⛁", NamedTextColor.GREEN));

            sp.sendMessage(msg);
            sp.playSound(sp.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
        }

        ItemStack give = fresh.item().clone();
        give.setAmount(toBuy);
        hideAttributes(give);
        HashMap<Integer, ItemStack> left = player.getInventory().addItem(give);
        if (!left.isEmpty()) {
            left.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
        }

        Component itemName = ItemLocalization.getNameComponent(fresh.item());
        Component msg = component(messages.message("notification.purchased-prefix"))
                .append(itemName.color(NamedTextColor.YELLOW))
                .append(component(messages.message("notification.price-separator")))
                .append(Component.text(formatMoney(totalPrice) + "⛁", NamedTextColor.GREEN));

        player.sendMessage(msg);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.25f);

        purchaseViews.remove(player.getUniqueId());
        player.closeInventory();
    }

    public void openSellerGui(Player viewer, UUID sellerId) {
        if (!hasActiveListings(sellerId)) {
            OfflinePlayer seller = Bukkit.getOfflinePlayer(sellerId);
            String sellerName = seller.getName() != null ? seller.getName() : sellerId.toString();
            viewer.sendMessage(messages.message("error.seller-empty", Map.of("seller", sellerName)));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        SellerView sv = new SellerView();
        sv.sellerId = sellerId;
        sv.slotToListingId = new HashMap<>();
        OfflinePlayer seller = Bukkit.getOfflinePlayer(sellerId);
        String sellerName = seller.getName() != null ? seller.getName() : sellerId.toString();
        Inventory inv = Bukkit.createInventory(sv, 54, ChatColor.DARK_GRAY
                + messages.message("gui.title.seller", Map.of("seller", sellerName)));
        sv.inventory = inv;
        sellerViews.put(viewer.getUniqueId(), sv);
        decoratePurchase(inv);
        int activeCount = sync().activeCount(sellerId);
        ItemStack info = new ItemStack(Material.MANGROVE_HANGING_SIGN);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.setDisplayName(messages.message("gui.seller.info-name"));
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(messages.message("gui.seller.info-title"));
            lore.add(messages.message("gui.seller.info-player", Map.of("seller", sellerName)));
            lore.add(messages.message("gui.seller.info-active", Map.of("amount", activeCount)));
            lore.add("");
            lore.add(messages.message("gui.seller.warning-title"));
            lore.add(messages.message("gui.seller.warning-line-1"));
            lore.add(messages.message("gui.seller.warning-line-2"));
            lore.add(messages.message("gui.seller.warning-line-3"));
            lore.add("");
            im.setLore(lore);
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            info.setItemMeta(im);
        }
        hideAttributes(info);
        inv.setItem(13, info);
        ItemStack back = createIcon(
                Material.RED_CANDLE, messages.message("gui.action.back")
        );
        inv.setItem(45, back);
        fillSellerInventory(sv);
        viewer.openInventory(inv);
    }

    void handleSellerClick(Player player, SellerView sv, int slot, boolean leftClick, boolean rightClick) {

        // --- BACK ---
        if (slot == 45) {
            sellerViews.remove(player.getUniqueId());
            openAuction(player);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
            return;
        }

        // --- SORT ---
        if (slot == 53) {
            if (leftClick) sv.sort = MarketFilter.nextSort(sv.sort);
            else if (rightClick) sv.sort = MarketFilter.prevSort(sv.sort);
            fillSellerInventory(sv);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            return;
        }

        // --- Покупка предмета ---
        String id = sv.slotToListingId.get(slot);
        if (id == null) return;

        MarketListing listing = loadListingById(id);
        if (listing == null || listing.amount() <= 0 || !"ACTIVE".equalsIgnoreCase(listing.status())) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            sv.inventory.setItem(slot, null);
            sv.slotToListingId.remove(slot);
            refreshAllViews();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }

        if (listing.sellerId().equals(player.getUniqueId())) {
            player.sendMessage(messages.message("error.own-listing"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }

        sellerViews.remove(player.getUniqueId());
        openPurchaseGui(player, listing);
    }


    private void showNoMoneyBarrier(Player player, AuctionView view, int slot, MarketListing listing) {
        Inventory inv = view.inventory;
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.message("gui.action.no-money"));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            barrier.setItemMeta(meta);
        }
        hideAttributes(barrier);
        inv.setItem(slot, barrier);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.9f, 0.7f);
        new BukkitRunnable() {
            @Override
            public void run() {
                Player p = player;
                if (p == null || !p.isOnline()) return;
                if (!p.getOpenInventory().getTopInventory().equals(inv)) return;
                fillAuctionInventory(p, view);
            }
        }.runTaskLater(plugin, 40L);
    }

    void handleAuctionClick(Player player, AuctionView view, int slot, boolean leftClick, boolean rightClick) {
        if (slot == SLOT_MY_ITEMS) {
            if (view.isSearch) {
                auctionViews.remove(player.getUniqueId());
                openAuction(player);
            } else {
                openMyItems(player);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
            return;
        }
        if (slot == SLOT_PREV_PAGE) {
            if (view.page > 0) {
                view.page--;
                fillAuctionInventory(player, view);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.95f);
            }
            return;
        }
        if (slot == SLOT_NEXT_PAGE) {
            view.page++;
            fillAuctionInventory(player, view);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.05f);
            return;
        }
        if (slot == SLOT_CATEGORY && !view.isSearch) {
            if (leftClick) {
                view.category = categories.next(view.category);
            } else if (rightClick) {
                view.category = categories.previous(view.category);
            }
            view.page = 0;
            fillAuctionInventory(player, view);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            return;
        }

        int sortSlot = view.isSearch ? 53 : SLOT_SORT;
        if (slot == sortSlot) {
            if (leftClick) {
                view.sort = MarketFilter.nextSort(view.sort);
            } else if (rightClick) {
                view.sort = MarketFilter.prevSort(view.sort);
            }
            view.page = 0;
            fillAuctionInventory(player, view);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            return;
        }

        String id = view.slotToListingId.get(slot);
        if (id == null) return;
        MarketListing listing = loadListingById(id);
        if (listing == null || listing.amount() <= 0) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            refreshAllViews();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        if (!"ACTIVE".equalsIgnoreCase(listing.status())) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            refreshAllViews();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        UUID viewerId = player.getUniqueId();
        if (listing.sellerId().equals(viewerId)) {
            repository.updateStatus(listing.id(), "RETURNED");
            sync().listingUpdated(listing.withStatus("RETURNED"));
            view.inventory.setItem(slot, null);
            view.slotToListingId.remove(slot);
            player.sendMessage(messages.message("success.listing-returned"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
            return;
        }
        double minCost = listing.pricePerUnit();
        if (!economy.has(player, minCost)) {
            player.sendMessage(messages.message("error.insufficient-funds"));
            showNoMoneyBarrier(player, view, slot, listing);
            return;
        }
        openPurchaseGui(player, listing);
    }

    void closeView(UUID viewerId, Inventory inventory) {
        if (inventory.getHolder() instanceof PurchaseView) purchaseViews.remove(viewerId);
        else if (inventory.getHolder() instanceof SellerView) sellerViews.remove(viewerId);
        else if (inventory.getHolder() instanceof MyItemsView) myItemsViews.remove(viewerId);
        else if (inventory.getHolder() instanceof AuctionView) auctionViews.remove(viewerId);
    }

    void removeViewer(UUID viewerId) {
        purchaseViews.remove(viewerId);
        myItemsViews.remove(viewerId);
        auctionViews.remove(viewerId);
        sellerViews.remove(viewerId);
    }

    ItemStack hideAttributes(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(
                    ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                    ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_ARMOR_TRIM,
                    ItemFlag.HIDE_DYE
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    private MarketListing loadListingById(String id) {
        return repository.findById(id).orElse(null);
    }
}
