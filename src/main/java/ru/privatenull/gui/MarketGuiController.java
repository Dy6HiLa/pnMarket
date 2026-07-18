package ru.privatenull.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import ru.privatenull.compat.MaterialCompat;
import ru.privatenull.currency.MarketPayment;
import ru.privatenull.localization.ItemLocalization;
import ru.privatenull.market.MarketFilter;
import ru.privatenull.market.MarketFilter.SortType;
import ru.privatenull.market.MarketBundle;
import ru.privatenull.market.MarketCategories;
import ru.privatenull.market.MarketSearch;
import ru.privatenull.market.MarketSync;
import ru.privatenull.config.GuiLabels;
import ru.privatenull.config.MessagesConfig;
import ru.privatenull.model.MarketListing;
import ru.privatenull.model.PurchaseReservation;
import ru.privatenull.storage.MarketStorage;
import ru.privatenull.pnlibrary.gui.GuiOpenAnimationService;
import ru.privatenull.pnlibrary.gui.GuiUpdateService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

    private static final int SLOT_DONATE_AUCTION = 45;
    private static final int SLOT_MY_ITEMS = 46;
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

    private static final int SLOT_BUNDLE_BACK = 49;

    private static final String BUNDLE_PREVIOUS_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjllYTFkODYyNDdmNGFmMzUxZWQxODY2YmNhNmEzMDQwYTA2YzY4MTc3Yzc4ZTQyMzE2YTEwOThlNjBmYjdkMyJ9fX0=";
    private static final String BUNDLE_NEXT_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODI3MWE0NzEwNDQ5NWUzNTdjM2U4ZTgwZjUxMWE5ZjEwMmIwNzAwY2E5Yjg4ZTg4Yjc5NWQzM2ZmMjAxMDVlYiJ9fX0=";
    private static final String BUNDLE_DISABLED_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjc1NDgzNjJhMjRjMGZhODQ1M2U0ZDkzZTY4YzU5NjlkZGJkZTU3YmY2NjY2YzAzMTljMWVkMWU4NGQ4OTA2NSJ9fX0=";

    // Prefer the 1.17+/1.19+ icons while retaining valid 1.16.5 fallbacks.
    private static final Material ICON_BUY = MaterialCompat.first("LIME_CANDLE", "LIME_WOOL");
    private static final Material ICON_INFO = MaterialCompat.first("MANGROVE_HANGING_SIGN", "OAK_SIGN");

    private static final int[] SELLER_SLOTS = {
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33
    };

    private final PnMarketPlugin plugin;
    private final MarketStorage repository;
    private final MarketPayment payment;
    private final MessagesConfig messages;
    private final GuiLabels guiLabels;
    private final MarketCategories categories;
    private final MarketSync sync;
    private final boolean donateAuction;
    private final GuiOpenAnimationService guiAnimations;
    private final GuiUpdateService guiUpdates = new GuiUpdateService();
    private final Map<String, ItemStack> texturedHeadCache = new HashMap<>();

    final Map<UUID, AuctionView> auctionViews = new HashMap<>();
    final Map<UUID, PurchaseView> purchaseViews = new HashMap<>();
    final Map<UUID, SellerView> sellerViews = new HashMap<>();
    final Map<UUID, MyItemsView> myItemsViews = new HashMap<>();

    public MarketGuiController(PnMarketPlugin plugin, MarketStorage repository, MarketPayment payment,
                               MessagesConfig messages, GuiLabels guiLabels, MarketCategories categories,
                               MarketSync sync, boolean donateAuction) {
        this.plugin = plugin;
        this.repository = repository;
        this.payment = payment;
        this.messages = messages;
        this.guiLabels = guiLabels;
        this.categories = categories;
        this.sync = sync;
        this.donateAuction = donateAuction;
        this.guiAnimations = new GuiOpenAnimationService(plugin);
    }

    public void shutdown() {
        guiAnimations.shutdown();
    }

    private void openGui(Player player, Inventory inventory) {
        Object currentHolder = player.getOpenInventory().getTopInventory().getHolder();
        if (currentHolder == inventory.getHolder()) {
            guiAnimations.cancel(player);
            player.openInventory(inventory);
            return;
        }
        guiAnimations.open(player, inventory, true);
    }

    private void setSlot(Inventory inventory, int slot, ItemStack item) {
        if (sameSlotItem(inventory.getItem(slot), item)) return;
        List<Player> viewers = inventory.getViewers().stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                .filter(viewer -> viewer.getOpenInventory().getTopInventory() == inventory)
                .toList();
        if (viewers.isEmpty()) {
            inventory.setItem(slot, item == null ? null : item.clone());
            return;
        }
        for (Player viewer : viewers) {
            guiAnimations.complete(viewer);
            guiUpdates.setTopSlot(viewer, inventory, slot, item);
        }
    }

    private boolean sameSlotItem(ItemStack current, ItemStack replacement) {
        boolean currentEmpty = current == null || current.getType().isAir();
        boolean replacementEmpty = replacement == null || replacement.getType().isAir();
        if (currentEmpty || replacementEmpty) return currentEmpty == replacementEmpty;
        return current.equals(replacement);
    }

    private MarketSync sync() {
        return sync;
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
        return payment.format(d);
    }

    private String formatPrice(double amount) {
        return plugin.formatPrice(donateAuction, amount, formatMoney(amount));
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
            meta.setDisplayName(ChatColor.RESET + name);
            if (loreLines != null && loreLines.length > 0) {
                meta.setLore(Arrays.asList(loreLines));
            }
            i.setItemMeta(meta);
        }
        return hideAttributes(i);
    }

    private ItemStack texturedHead(String textureBase64, String name, String... loreLines) {
        String cacheKey = textureBase64 + '\u0000' + name + '\u0000' + String.join("\u0000", loreLines);
        ItemStack cached = texturedHeadCache.get(cacheKey);
        if (cached != null) return cached.clone();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;
        meta.setDisplayName(ChatColor.RESET + name);
        meta.setLore(Arrays.asList(loreLines));
        try {
            if (!applyModernSkullProfile(meta, textureBase64)) {
                applyLegacySkullProfile(meta, textureBase64);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The menu remains usable with a normal player head if a server changes its skull internals.
        }
        head.setItemMeta(meta);
        head = hideAttributes(head);
        texturedHeadCache.put(cacheKey, head.clone());
        return head;
    }

    /** Uses Paper's public profile API on 1.19+, without linking pnMarket to a newer Bukkit API. */
    private boolean applyModernSkullProfile(SkullMeta meta, String textureBase64) {
        try {
            URL skinUrl = textureUrl(textureBase64);
            if (skinUrl == null) return false;

            Class<?> profileType = Class.forName("org.bukkit.profile.PlayerProfile");
            Class<?> texturesType = Class.forName("org.bukkit.profile.PlayerTextures");
            UUID profileId = textureProfileId(textureBase64);
            // Paper's two-argument factory is the stable form used by current Paper servers.
            Object profile = Bukkit.class.getMethod("createPlayerProfile", UUID.class, String.class)
                    .invoke(null, profileId, "pn" + profileId.toString().replace("-", "").substring(0, 14));
            Object textures = profileType.getMethod("getTextures").invoke(profile);
            texturesType.getMethod("setSkin", URL.class).invoke(textures, skinUrl);
            profileType.getMethod("setTextures", texturesType).invoke(profile, textures);
            SkullMeta.class.getMethod("setOwnerProfile", profileType).invoke(meta, profile);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /** Uses the exact supplied Base64 payload on legacy servers such as 1.16.5. */
    private void applyLegacySkullProfile(SkullMeta meta, String textureBase64) throws ReflectiveOperationException {
        GameProfile profile = new GameProfile(textureProfileId(textureBase64), "pnMarket");
        profile.getProperties().put("textures", new Property("textures", textureBase64));
        Field profileField = findProfileField(meta.getClass());
        profileField.setAccessible(true);
        profileField.set(meta, profile);
    }

    private URL textureUrl(String textureBase64) {
        try {
            String json = new String(Base64.getDecoder().decode(textureBase64), StandardCharsets.UTF_8);
            int start = json.indexOf("https://textures.minecraft.net/texture/");
            if (start < 0) start = json.indexOf("http://textures.minecraft.net/texture/");
            if (start < 0) return null;
            int end = json.indexOf('"', start);
            return new URL(json.substring(start, end < 0 ? json.length() : end));
        } catch (IllegalArgumentException | java.net.MalformedURLException ignored) {
            return null;
        }
    }

    private UUID textureProfileId(String textureBase64) {
        return UUID.nameUUIDFromBytes(("pnMarket:" + textureBase64).getBytes(StandardCharsets.UTF_8));
    }

    private Field findProfileField(Class<?> type) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField("profile");
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("profile");
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
        lore.add(" §7- §fСтоимость: " + formatPrice(totalPrice));
        if (amountForLore > 1) {
            lore.add(messages.message("listing.amount", Map.of("amount", amountForLore)));
        }
        lore.add(messages.message("listing.expires", Map.of("time", time)));
        lore.add("");
        return lore;
    }

    private void addBundleActionLore(List<String> lore, boolean ownListing) {
        lore.add("§x§F§F§8§7§0§2➥ §fНажмите, §eЛКМ §fчтобы посмотреть содержимое");
        if (ownListing) {
            lore.add("§x§F§F§0§0§0§0➥ §fНажмите, §cПКМ §fчтобы снять с продажи");
        } else {
            lore.add("§x§7§C§F§F§8§0➥ §fНажмите, §aПКМ §fчтобы купить");
        }
    }

    private boolean isBundle(MarketListing listing) {
        return MarketBundle.isBundle(plugin, listing.item());
    }

    private String bundleDisplayName(MarketListing listing) {
        int numericId = Math.floorMod(listing.id().hashCode(), 1_000_000);
        return "§6Набор §8#" + String.format(Locale.ROOT, "%06d", numericId);
    }

    private List<ItemStack> bundleItems(MarketListing listing) {
        return MarketBundle.contents(plugin, listing.item());
    }

    private List<ItemStack> deliveryItems(MarketListing listing, int amount) {
        if (isBundle(listing)) return bundleItems(listing);
        ItemStack item = listing.item().clone();
        item.setAmount(amount);
        return List.of(item);
    }

    private boolean canFitAll(Player player, List<ItemStack> items) {
        ItemStack[] simulated = player.getInventory().getStorageContents();
        for (int index = 0; index < simulated.length; index++) {
            if (simulated[index] != null) simulated[index] = simulated[index].clone();
        }

        for (ItemStack source : items) {
            if (source == null || source.getType().isAir()) continue;
            int remaining = source.getAmount();
            for (ItemStack stored : simulated) {
                if (stored == null || !stored.isSimilar(source)) continue;
                int space = stored.getMaxStackSize() - stored.getAmount();
                if (space <= 0) continue;
                int added = Math.min(space, remaining);
                stored.setAmount(stored.getAmount() + added);
                remaining -= added;
                if (remaining == 0) break;
            }
            if (remaining > 0) {
                for (int index = 0; index < simulated.length && remaining > 0; index++) {
                    if (simulated[index] != null && !simulated[index].getType().isAir()) continue;
                    ItemStack placed = source.clone();
                    int added = Math.min(placed.getMaxStackSize(), remaining);
                    placed.setAmount(added);
                    simulated[index] = placed;
                    remaining -= added;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private void giveItemsOrDrop(Player player, List<ItemStack> items) {
        ItemStack[] delivery = items.stream()
                .filter(item -> item != null && !item.getType().isAir())
                .map(ItemStack::clone)
                .toArray(ItemStack[]::new);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(delivery);
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
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
        view.controller = this;
        view.viewer = uuid;
        view.category = category;
        view.sort = sort;
        view.searchQuery = searchQuery;
        view.page = page;
        view.isSearch = isSearch;
        view.slotToListingId = new HashMap<>();

        List<MarketListing> filtered = getFilteredListings(view);
        int pageSize = AUCTION_SLOTS.length;
        int totalPages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        if (view.page < 0) view.page = 0;
        if (view.page >= totalPages) view.page = totalPages - 1;

        String baseTitle = (searchQuery != null && !searchQuery.isEmpty())
                ? searchQuery : auctionTitle();
        String title = messages.message("gui.title.auction-pages", Map.of(
                "name", baseTitle, "page", view.page + 1, "pages", totalPages
        ));

        Inventory inv = Bukkit.createInventory(view, 54, title);
        view.inventory = inv;
        auctionViews.put(uuid, view);

        decorateAuction(inv, isSearch);
        initFilterIcons(view);

        fillAuctionInventory(player, view, false);
        openGui(player, inv);
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

    private String auctionTitle() {
        return donateAuction ? "Донат-аукцион" : messages.message("gui.title.auction");
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
            setSlot(inv, slot, black);
        }
        for (int slot : AUCTION_ORANGE_SLOTS) {
            setSlot(inv, slot, orange);
        }

        if (isSearch) {
            ItemStack back = texturedHead(
                    BUNDLE_PREVIOUS_TEXTURE,
                    messages.message("gui.action.back"),
                    "",
                    messages.message("gui.search.title"),
                    messages.message("gui.search.line-1"),
                    messages.message("gui.search.line-2"),
                    ""
            );
            setSlot(inv, SLOT_DONATE_AUCTION, back);
        } else {
            Material icon = donateAuction ? Material.GOLD_INGOT : Material.NETHER_STAR;
            setSlot(inv, SLOT_DONATE_AUCTION, createIcon(
                    icon, "§x§D§5§B§F§F§F Смена аукциона",
                    "",
                    " §7- §fТекущий аукцион: "
                            + (donateAuction ? "§x§E§A§7§3§3§AДонат-аукцион" : "§x§3§A§E§A§4§DОбычный аукцион"),
                    "",
                    "§x§D§5§B§F§F§F «Доступные аукционы»",
                    (donateAuction ? " §7- §f" : " §x§B§4§E§E§4§1» ")
                            + "§x§3§A§E§A§4§DОбычный аукцион ",
                    (donateAuction ? " §x§B§4§E§E§4§1» " : " §7- §f")
                            + "§x§E§A§7§3§3§AДонат-аукцион ",
                    "",
                    messages.message("gui.action.open")
            ));
        }
        ItemStack myItems = createIcon(
                Material.BARREL,
                donateAuction ? "§x§D§5§B§F§F§F Мои донат-товары" : messages.message("gui.action.my-items"),
                "",
                " §7- §fЗдесь находятся ваши лоты,",
                "    §fкоторые истекли или были",
                "    §fвозвращены с аукциона.",
                "",
                "§x§D§5§B§F§F§F «Текущий раздел»",
                (donateAuction ? " §7- §f" : " §x§B§4§E§E§4§1» ")
                        + "§x§3§A§E§A§4§DМои товары",
                (donateAuction ? " §x§B§4§E§E§4§1» " : " §7- §f")
                        + "§x§E§A§7§3§3§AМои донат-товары",
                "",
                messages.message("gui.action.open")
        );
        setSlot(inv, SLOT_MY_ITEMS, myItems);
    }

    private void initFilterIcons(AuctionView view) {
        if (view.isSearch) {
            updateSortIcon(view);
            setSlot(view.inventory, SLOT_CATEGORY, null);
            return;
        }

        updateCategoryIcon(view, categoryCounts(), activeListings().size());
        updateSortIcon(view);
    }

    void fillAuctionInventory(Player player, AuctionView view) {
        fillAuctionInventory(player, view, true);
    }

    private void fillAuctionInventory(Player player, AuctionView view, boolean updateTitle) {
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
        setSlot(inv, SLOT_PREV_PAGE, texturedHead(BUNDLE_DISABLED_TEXTURE, "§8Предыдущей страницы нет",
                "", "§7Вы уже на первой странице.", ""));
        setSlot(inv, SLOT_NEXT_PAGE, texturedHead(BUNDLE_DISABLED_TEXTURE, "§8Следующей страницы нет",
                "", "§7Это последняя страница.", ""));

        if (view.page > 0) {
            ItemStack prev = texturedHead(
                    BUNDLE_PREVIOUS_TEXTURE, "§e§l← Предыдущая страница",
                    "§8━━━━━━━━━━━━━━━━", "§7Перейти на страницу §f" + view.page, "", "§e▸ Нажмите, чтобы открыть"
            );
            setSlot(inv, SLOT_PREV_PAGE, prev);
        }

        if (view.page < totalPages - 1) {
            ItemStack next = texturedHead(
                    BUNDLE_NEXT_TEXTURE, "§e§lСледующая страница →",
                    "§8━━━━━━━━━━━━━━━━", "§7Перейти на страницу §f" + (view.page + 2), "", "§e▸ Нажмите, чтобы открыть"
            );
            setSlot(inv, SLOT_NEXT_PAGE, next);
        }

        if (!view.isSearch) {
            updateCategoryIcon(view, categoryCounts(), activeListings().size());
        }
        updateSortIcon(view);

        int index = startIndex;
        for (int slot : AUCTION_SLOTS) {
            if (index >= endIndex) {
                setSlot(inv, slot, null);
                continue;
            }
            MarketListing listing = filtered.get(index++);
            ItemStack display = listing.item().clone();
            int displayAmount = Math.min(listing.amount(), display.getMaxStackSize());
            if (displayAmount <= 0) displayAmount = 1;
            display.setAmount(displayAmount);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                if (isBundle(listing)) {
                    meta.setDisplayName(bundleDisplayName(listing));
                } else if (!meta.hasDisplayName()) {
                    meta.setDisplayName(ChatColor.RESET + ItemLocalization.getPlainName(listing.item()));
                }
                List<String> lore = isBundle(listing)
                        ? new ArrayList<>()
                        : meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.addAll(buildListingLore(listing, listing.amount()));
                boolean ownListing = listing.sellerId().equals(player.getUniqueId());
                if (isBundle(listing)) {
                    addBundleActionLore(lore, ownListing);
                } else if (ownListing) {
                    lore.add(messages.message("gui.action.collect"));
                } else {
                    lore.add(messages.message("gui.action.purchase"));
                }
                meta.setLore(lore);
                applyDisplayFlags(meta);
                display.setItemMeta(meta);
            }
            hideAttributes(display);
            setSlot(inv, slot, display);
            view.slotToListingId.put(slot, listing.id());
        }

        String baseTitle = (view.searchQuery != null && !view.searchQuery.isEmpty())
                ? view.searchQuery : auctionTitle();
        String newTitle = messages.message("gui.title.auction-pages", Map.of(
                "name", baseTitle, "page", view.page + 1, "pages", totalPages
        ));
        if (updateTitle && !player.getOpenInventory().getTitle().equals(newTitle)) {
            if (!guiUpdates.setTitle(player, inv, newTitle)) {
                Inventory newInv = Bukkit.createInventory(view, 54, newTitle);
                newInv.setContents(inv.getContents());
                view.inventory = newInv;
                openGui(player, newInv);
            }
        }
    }

    private void updateCategoryIcon(AuctionView view, Map<String, Integer> counts, int allCount) {
        if (view.isSearch) {
            setSlot(view.inventory, SLOT_CATEGORY, null);
            return;
        }
        Inventory inv = view.inventory;
        ItemStack item = new ItemStack(ICON_INFO);
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
            applyDisplayFlags(meta);
            item.setItemMeta(meta);
        }
        hideAttributes(item);
        setSlot(inv, SLOT_CATEGORY, item);
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
            applyDisplayFlags(meta);
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
            setSlot(inv, 52, black);
        }
        setSlot(inv, sortSlot, item);
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
            applyDisplayFlags(meta);
            item.setItemMeta(meta);
        }
        hideAttributes(item);
        setSlot(view.inventory, 53, item);
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
                setSlot(view.inventory, slot, null);
                continue;
            }
            MarketListing listing = listings.get(index++);
            ItemStack display = listing.item().clone();
            display.setAmount(Math.max(1, Math.min(listing.amount(), display.getMaxStackSize())));
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                if (isBundle(listing)) meta.setDisplayName(bundleDisplayName(listing));
                List<String> lore = new ArrayList<>(buildListingLore(listing, listing.amount()));
                if (isBundle(listing)) addBundleActionLore(lore, false);
                else lore.add(messages.message("gui.action.purchase"));
                meta.setLore(lore);
                applyDisplayFlags(meta);
                display.setItemMeta(meta);
            }
            hideAttributes(display);
                setSlot(view.inventory, slot, display);
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
        view.controller = this;
        view.inventory = Bukkit.createInventory(view, 54,
                ChatColor.DARK_GRAY + (donateAuction ? "Мои донат-товары" : messages.message("gui.title.my-items")));
        view.slotToListingId = new HashMap<>();
        myItemsViews.put(viewerId, view);
        fillMyItemsInventory(viewerId, view);
        openGui(player, view.inventory);
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
                setSlot(view.inventory, slot, null);
                continue;
            }
            MarketListing listing = listings.next();
            ItemStack display = listing.item().clone();
            display.setAmount(Math.max(1, Math.min(listing.amount(), display.getMaxStackSize())));
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                if (isBundle(listing)) meta.setDisplayName(bundleDisplayName(listing));
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
                applyDisplayFlags(meta);
                display.setItemMeta(meta);
            }
            hideAttributes(display);
            setSlot(view.inventory, slot, display);
            view.slotToListingId.put(slot, listing.id());
        }
        setSlot(view.inventory, SLOT_BACK_BOTTOM,
                texturedHead(BUNDLE_PREVIOUS_TEXTURE, messages.message("gui.action.back")));
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
            setSlot(view.inventory, slot, null);
            view.slotToListingId.remove(slot);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        giveItemsOrDrop(player, deliveryItems(listing, listing.amount()));
        repository.delete(listing.id());
        sync().listingRemoved(listing.id());
        Component itemName = ItemLocalization.getNameComponent(listing.item());
        player.sendMessage(component(messages.message("notification.collected-prefix"))
                .append(itemName.color(NamedTextColor.YELLOW)));
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.9f, 1.1f);
        setSlot(view.inventory, slot, null);
        view.slotToListingId.remove(slot);
    }

    private void openBundlePreview(Player player, MarketListing listing) {
        List<ItemStack> contents = bundleItems(listing);
        if (contents.isEmpty()) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }

        BundlePreviewView view = new BundlePreviewView();
        view.controller = this;
        view.listing = listing;
        view.inventory = Bukkit.createInventory(view, 54,
                bundleDisplayName(listing));
        fillBundlePreview(player, view);
        openGui(player, view.inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
    }

    private void fillBundlePreview(Player player, BundlePreviewView view) {
        List<ItemStack> contents = bundleItems(view.listing);
        decorateBundlePreview(view.inventory);
        for (int index = 0; index < AUCTION_SLOTS.length; index++) {
            if (index >= contents.size()) {
                setSlot(view.inventory, AUCTION_SLOTS[index], null);
                continue;
            }
            ItemStack display = contents.get(index).clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null && !meta.hasDisplayName()) {
                meta.setDisplayName(ChatColor.RESET + ItemLocalization.getPlainName(display));
                applyDisplayFlags(meta);
                display.setItemMeta(meta);
            }
            setSlot(view.inventory, AUCTION_SLOTS[index], display);
        }

        setSlot(view.inventory, SLOT_BUNDLE_BACK,
                texturedHead(BUNDLE_PREVIOUS_TEXTURE, messages.message("gui.action.back")));
    }

    private void decorateBundlePreview(Inventory inventory) {
        ItemStack black = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta blackMeta = black.getItemMeta();
        if (blackMeta != null) {
            blackMeta.setDisplayName(" ");
            black.setItemMeta(blackMeta);
        }
        hideAttributes(black);

        ItemStack orange = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta orangeMeta = orange.getItemMeta();
        if (orangeMeta != null) {
            orangeMeta.setDisplayName(" ");
            orange.setItemMeta(orangeMeta);
        }
        hideAttributes(orange);

        for (int slot : AUCTION_BLACK_SLOTS) setSlot(inventory, slot, black);
        for (int slot : AUCTION_ORANGE_SLOTS) setSlot(inventory, slot, orange);
    }

    void handleBundlePreviewClick(Player player, BundlePreviewView view, int slot) {
        if (slot != SLOT_BUNDLE_BACK) return;
        openAuction(player);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
    }

    private void openPurchaseGui(Player player, MarketListing listing) {
        PurchaseView view = new PurchaseView();
        view.controller = this;
        view.listing = listing;
        view.maxAmount = listing.amount();
        view.quantity = 1;
        view.sellerId = listing.sellerId();
        view.inventory = Bukkit.createInventory(view, 54,
                ChatColor.DARK_GRAY + (donateAuction ? "Покупка за PlayerPoints" : messages.message("gui.title.purchase")));
        purchaseViews.put(player.getUniqueId(), view);
        decoratePurchase(view.inventory);
        setSlot(view.inventory, SLOT_BACK_TOP,
                texturedHead(BUNDLE_PREVIOUS_TEXTURE, messages.message("gui.action.back")));
        setSlot(view.inventory, SLOT_MINUS_1, createIcon(Material.RED_WOOL, "§c-1"));
        setSlot(view.inventory, SLOT_MINUS_10, createIcon(Material.RED_WOOL, "§c-10"));
        setSlot(view.inventory, SLOT_PLUS_1, createIcon(Material.GREEN_WOOL, "§a+1"));
        setSlot(view.inventory, SLOT_PLUS_10, createIcon(Material.GREEN_WOOL, "§a+10"));
        Inventory inv = view.inventory;
        PurchaseView pv = view;

        ItemStack buy = createIcon(
                ICON_BUY, messages.message("gui.action.buy")
        );
        setSlot(inv, SLOT_BUY, buy);

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
            applyDisplayFlags(sm);
            head.setItemMeta(sm);
        }
        hideAttributes(head);
        setSlot(inv, SLOT_SELLER_HEAD, head);

        updateQuantityItems(pv);
        openGui(player, inv);
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
            setSlot(inv, slot, black);
        }
        for (int slot : PURCHASE_ORANGE_SLOTS) {
            setSlot(inv, slot, orange);
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
            if (isBundle(listing)) pm.setDisplayName(bundleDisplayName(listing));
            List<String> lore = new ArrayList<>();
            lore.addAll(buildListingLore(listing, pv.quantity));
            pm.setLore(lore);
            applyDisplayFlags(pm);
            preview.setItemMeta(pm);
        }
        hideAttributes(preview);

        setSlot(inv, SLOT_PREVIEW, preview);
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
        if (!payment.has(player, totalPrice)) {
            player.sendMessage(messages.message("error.insufficient-funds"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        List<ItemStack> delivery = deliveryItems(fresh, requestedAmount);
        if (delivery.isEmpty()) {
            player.sendMessage(messages.message("error.listing-unavailable"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }
        if (!canFitAll(player, delivery)) {
            player.sendMessage("§cНедостаточно места в инвентаре для покупки этого набора.");
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
        delivery = deliveryItems(fresh, toBuy);
        if (delivery.isEmpty() || !canFitAll(player, delivery)) {
            repository.rollbackReservation(fresh.id(), toBuy);
            player.sendMessage("§cНедостаточно места в инвентаре для покупки этого набора.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            refreshAllViews();
            return;
        }
        if (!payment.withdraw(player, totalPrice)) {
            repository.rollbackReservation(fresh.id(), toBuy);
            player.sendMessage(messages.message("error.insufficient-funds"));
            refreshAllViews();
            return;
        }
        OfflinePlayer seller = Bukkit.getOfflinePlayer(fresh.sellerId());
        if (!payment.deposit(seller, totalPrice)) {
            payment.deposit(player, totalPrice);
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
                    .append(component(formatPrice(totalPrice)));

            sp.sendMessage(msg);
            sp.playSound(sp.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
        }

        giveItemsOrDrop(player, delivery);

        Component itemName = ItemLocalization.getNameComponent(fresh.item());
        Component msg = component(messages.message("notification.purchased-prefix"))
                .append(itemName.color(NamedTextColor.YELLOW))
                .append(component(messages.message("notification.price-separator")))
                .append(component(formatPrice(totalPrice)));

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
        sv.controller = this;
        sv.sellerId = sellerId;
        sv.slotToListingId = new HashMap<>();
        OfflinePlayer seller = Bukkit.getOfflinePlayer(sellerId);
        String sellerName = seller.getName() != null ? seller.getName() : sellerId.toString();
        Inventory inv = Bukkit.createInventory(sv, 54, ChatColor.DARK_GRAY
                + (donateAuction ? "Донат-товары " + sellerName
                : messages.message("gui.title.seller", Map.of("seller", sellerName))));
        sv.inventory = inv;
        sellerViews.put(viewer.getUniqueId(), sv);
        decoratePurchase(inv);
        int activeCount = sync().activeCount(sellerId);
        ItemStack info = new ItemStack(ICON_INFO);
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
            applyDisplayFlags(im);
            info.setItemMeta(im);
        }
        hideAttributes(info);
        setSlot(inv, 13, info);
        ItemStack back = texturedHead(
                BUNDLE_PREVIOUS_TEXTURE, messages.message("gui.action.back")
        );
        setSlot(inv, 45, back);
        fillSellerInventory(sv);
        openGui(viewer, inv);
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
            setSlot(sv.inventory, slot, null);
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
        if (isBundle(listing) && leftClick) openBundlePreview(player, listing);
        else openPurchaseGui(player, listing);
    }


    private void showNoMoneyBarrier(Player player, AuctionView view, int slot, MarketListing listing) {
        Inventory inv = view.inventory;
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.message("gui.action.no-money"));
            applyDisplayFlags(meta);
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
        if (slot == SLOT_DONATE_AUCTION) {
            if (view.isSearch) openAuction(player);
            else plugin.openAuction(player, !donateAuction);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
            return;
        }
        if (slot == SLOT_MY_ITEMS) {
            openMyItems(player);
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
            if (isBundle(listing) && leftClick) {
                openBundlePreview(player, listing);
                return;
            }
            repository.updateStatus(listing.id(), "RETURNED");
            sync().listingUpdated(listing.withStatus("RETURNED"));
            setSlot(view.inventory, slot, null);
            view.slotToListingId.remove(slot);
            player.sendMessage(messages.message("success.listing-returned"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
            return;
        }
        if (isBundle(listing) && leftClick) {
            openBundlePreview(player, listing);
            return;
        }
        double minCost = listing.pricePerUnit();
        if (!payment.has(player, minCost)) {
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

    public void removeViewer(UUID viewerId) {
        purchaseViews.remove(viewerId);
        myItemsViews.remove(viewerId);
        auctionViews.remove(viewerId);
        sellerViews.remove(viewerId);
    }

    ItemStack hideAttributes(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyDisplayFlags(meta);
            if (MarketBundle.isBundle(plugin, item)) {
                if (!meta.hasDisplayName()) meta.setDisplayName("§6Набор");
                try {
                    meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
                } catch (IllegalArgumentException ignored) {
                    // Legacy server versions do not expose this item flag.
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyDisplayFlags(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
    }

    private MarketListing loadListingById(String id) {
        return repository.findById(id).orElse(null);
    }
}
