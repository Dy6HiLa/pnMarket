package ru.privatenull.market;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.localization.ItemLocalization;
import ru.privatenull.model.MarketListing;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class FavoriteService {
    private final PnMarketPlugin plugin;
    private final File file;
    private final Map<UUID, Map<Boolean, List<FavoriteFilter>>> favorites = new LinkedHashMap<>();

    public FavoriteService(PnMarketPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "favorites.yml");
        load();
    }

    public synchronized List<FavoriteFilter> list(UUID playerId, boolean donate) {
        return List.copyOf(favorites.getOrDefault(playerId, Map.of()).getOrDefault(donate, List.of()));
    }

    public synchronized AddResult addMaterial(UUID playerId, boolean donate, String rawMaterial) {
        Material material = ItemLocalization.matchMaterial(rawMaterial);
        if (material == null || material.isAir()) return AddResult.INVALID;
        return add(playerId, donate, FavoriteFilter.Type.MATERIAL, material.name(), 0);
    }

    public synchronized AddResult addName(UUID playerId, boolean donate, String rawName) {
        String value = normalize(rawName);
        if (value.length() < 2 || value.length() > 32) return AddResult.INVALID;
        return add(playerId, donate, FavoriteFilter.Type.NAME, value, 0);
    }

    public synchronized AddResult addPrice(UUID playerId, boolean donate, Material material, double price) {
        if (material == null || material.isAir()) return AddResult.INVALID;
        return addPrice(playerId, donate, material.name(), price);
    }

    public synchronized AddResult addPrice(UUID playerId, boolean donate, String itemKey, double price) {
        Material material = ItemLocalization.getKeyMaterial(itemKey);
        if (material == null || material.isAir()) return AddResult.INVALID;
        List<FavoriteFilter> values = mutableList(playerId, donate);
        for (int index = 0; index < values.size(); index++) {
            FavoriteFilter filter = values.get(index);
            if (filter.type() != FavoriteFilter.Type.PRICE
                    || !filter.value().equalsIgnoreCase(itemKey)) continue;
            values.set(index, new FavoriteFilter(filter.id(), filter.type(), filter.value(), Math.max(0, price),
                    filter.enchantment(), filter.enchantmentLevel()));
            save();
            return AddResult.UPDATED;
        }
        return add(playerId, donate, FavoriteFilter.Type.PRICE, itemKey, Math.max(0, price));
    }

    public synchronized FavoriteFilter priceFilter(UUID playerId, boolean donate, Material material) {
        if (material == null) return null;
        return priceFilter(playerId, donate, material.name());
    }

    public synchronized FavoriteFilter priceFilter(UUID playerId, boolean donate, String itemKey) {
        return list(playerId, donate).stream()
                .filter(filter -> filter.type() == FavoriteFilter.Type.PRICE
                        && filter.value().equalsIgnoreCase(itemKey))
                .findFirst().orElse(null);
    }

    public synchronized FavoriteFilter enchantmentFilter(UUID playerId, boolean donate, Material material,
                                                          Enchantment enchantment) {
        if (material == null || enchantment == null) return null;
        String key = enchantment.getKey().toString();
        return list(playerId, donate).stream()
                .filter(filter -> filter.type() == FavoriteFilter.Type.PRICE
                        && filter.value().equalsIgnoreCase(material.name())
                        && filter.enchantments().containsKey(key))
                .findFirst().orElse(null);
    }

    public synchronized AddResult setEnchantment(UUID playerId, boolean donate, Material material,
                                                  Enchantment enchantment, int level, double price) {
        if (material == null || material.isAir() || enchantment == null) return AddResult.INVALID;
        FavoriteFilter existing = priceFilter(playerId, donate, material.name());
        Map<String, Integer> conditions = existing == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(existing.enchantments());
        String enchantmentKey = enchantment.getKey().toString();
        if (level <= 0) {
            conditions.remove(enchantmentKey);
            if (existing != null && conditions.isEmpty()) remove(playerId, donate, existing.id());
            else if (existing != null) {
                replaceFilter(playerId, donate, existing, existing.withEnchantments(conditions));
                save();
            }
            return AddResult.UPDATED;
        }
        int normalizedLevel = Math.min(Math.max(1, level), Math.max(1, enchantment.getMaxLevel()));
        conditions.put(enchantmentKey, normalizedLevel);
        if (existing != null) {
            replaceFilter(playerId, donate, existing, existing.withEnchantments(conditions));
            save();
            return AddResult.UPDATED;
        }
        List<FavoriteFilter> values = mutableList(playerId, donate);
        int maximum = Math.max(1, plugin.getConfig().getInt("notifications.max-favorites", 100));
        if (values.size() >= maximum) return AddResult.LIMIT;
        FavoriteFilter filter = new FavoriteFilter(UUID.randomUUID().toString(), FavoriteFilter.Type.PRICE,
                material.name(), Math.max(0, price)).withEnchantments(conditions);
        values.add(filter);
        save();
        return AddResult.ADDED;
    }

    public synchronized boolean remove(UUID playerId, boolean donate, String id) {
        List<FavoriteFilter> values = mutableList(playerId, donate);
        boolean removed = values.removeIf(filter -> filter.id().equals(id));
        if (removed) save();
        return removed;
    }

    public synchronized void clear(UUID playerId, boolean donate) {
        mutableList(playerId, donate).clear();
        save();
    }

    public synchronized void notifyListing(MarketListing listing, boolean donate) {
        if (!plugin.getConfig().getBoolean("notifications.enabled", true)) return;
        ItemStack item = listing.item();
        String itemName = ItemLocalization.getPlainName(item);
        double unitPrice = listing.pricePerUnit();
        boolean changed = false;

        for (Map.Entry<UUID, Map<Boolean, List<FavoriteFilter>>> playerEntry : favorites.entrySet()) {
            UUID playerId = playerEntry.getKey();
            if (playerId.equals(listing.sellerId())) continue;
            List<FavoriteFilter> filters = playerEntry.getValue().getOrDefault(donate, List.of());
            FavoriteFilter matched = firstMatch(filters, item, itemName);
            if (matched == null) continue;

            String messageKey = "notification.favorite-listing";
            Map<String, Object> replacements = new HashMap<>();
            replacements.put("item", itemName);
            replacements.put("filter", filterLabel(matched));
            replacements.put("command", (donate ? "/dah" : "/ah") + " search " + itemName);
            replacements.put("price", plugin.formatPrice(donate, unitPrice, null));
            replacements.put("enchantment", enchantmentSummary(matched));
            replacements.put("level", matched.enchantmentLevel());
            replacements.put("condition", matched.hasEnchantment()
                    ? enchantmentSummary(matched) : "без дополнительных условий");

            if (matched.type() == FavoriteFilter.Type.PRICE) {
                double previous = matched.maximumPrice();
                messageKey = previous > 0 && unitPrice < previous
                        ? "notification.price-lowered" : "notification.price-appearance";
                replacements.put("old-price", previous > 0
                        ? plugin.formatPrice(donate, previous, null) : plugin.messages().message("favorites.filter.no-price"));
                if (previous <= 0 || unitPrice < previous) {
                    replaceFilter(playerId, donate, matched, new FavoriteFilter(matched.id(), matched.type(),
                            matched.value(), unitPrice, matched.enchantment(), matched.enchantmentLevel()));
                    changed = true;
                }
            }
            plugin.queueNotification(playerId, plugin.messages().message(messageKey, replacements));
        }
        if (changed) save();
    }

    public String filterLabel(FavoriteFilter filter) {
        String key = filter.hasEnchantment() ? "favorites.filter.enchantment" : switch (filter.type()) {
            case MATERIAL -> "favorites.filter.material";
            case NAME -> "favorites.filter.name";
            case PRICE -> "favorites.filter.price";
        };
        return plugin.messages().message(key, Map.of(
                "value", displayValue(filter),
                "enchantment", enchantmentSummary(filter),
                "level", filter.enchantmentLevel(),
                "price", filter.maximumPrice() > 0
                        ? plugin.formatPrice(false, filter.maximumPrice(), null)
                        : plugin.messages().message("favorites.filter.no-price")));
    }

    public String displayValue(FavoriteFilter filter) {
        if (filter.type() == FavoriteFilter.Type.NAME) return filter.value();
        return ItemLocalization.getItemName(filter.value());
    }

    public String enchantmentSummary(FavoriteFilter filter) {
        if (!filter.hasEnchantment()) return "Без условий";
        return filter.enchantments().entrySet().stream().map(entry -> {
            NamespacedKey key = NamespacedKey.fromString(entry.getKey());
            Enchantment enchantment = key == null ? null : Enchantment.getByKey(key);
            return ItemLocalization.getEnchantmentName(enchantment) + " " + entry.getValue();
        }).collect(java.util.stream.Collectors.joining(", "));
    }

    private FavoriteFilter firstMatch(List<FavoriteFilter> filters, ItemStack item, String itemName) {
        String itemKey = ItemLocalization.getItemKey(item);
        for (FavoriteFilter filter : filters) {
            if (filter.type() == FavoriteFilter.Type.PRICE
                    && filter.hasEnchantment()
                    && item.getType().name().equalsIgnoreCase(filter.value())
                    && matchesEnchantments(item, filter)) return filter;
        }
        for (FavoriteFilter filter : filters) {
            if (filter.type() == FavoriteFilter.Type.PRICE && !filter.hasEnchantment()
                    && itemKey.equalsIgnoreCase(filter.value())) return filter;
        }
        String normalizedName = normalize(itemName);
        for (FavoriteFilter filter : filters) {
            if (filter.type() == FavoriteFilter.Type.MATERIAL
                    && item.getType().name().equalsIgnoreCase(filter.value())) return filter;
            if (filter.type() == FavoriteFilter.Type.NAME
                    && normalizedName.contains(normalize(filter.value()))) return filter;
        }
        return null;
    }

    private boolean matchesEnchantments(ItemStack item, FavoriteFilter filter) {
        for (Map.Entry<String, Integer> condition : filter.enchantments().entrySet()) {
            NamespacedKey key = NamespacedKey.fromString(condition.getKey());
            Enchantment enchantment = key == null ? null : Enchantment.getByKey(key);
            if (enchantment == null) return false;
            int level = item.getEnchantmentLevel(enchantment);
            if (item.getItemMeta() instanceof EnchantmentStorageMeta stored) {
                level = Math.max(level, stored.getStoredEnchantLevel(enchantment));
            }
            if (level < condition.getValue()) return false;
        }
        return true;
    }

    private AddResult add(UUID playerId, boolean donate, FavoriteFilter.Type type,
                          String value, double maximumPrice) {
        List<FavoriteFilter> values = mutableList(playerId, donate);
        if (values.stream().anyMatch(filter -> filter.type() == type
                && filter.value().equalsIgnoreCase(value))) return AddResult.DUPLICATE;
        int maximum = Math.max(1, plugin.getConfig().getInt("notifications.max-favorites", 100));
        if (values.size() >= maximum) return AddResult.LIMIT;
        values.add(new FavoriteFilter(UUID.randomUUID().toString(), type, value, maximumPrice));
        save();
        return AddResult.ADDED;
    }

    private void replaceFilter(UUID playerId, boolean donate, FavoriteFilter oldFilter,
                               FavoriteFilter newFilter) {
        List<FavoriteFilter> values = mutableList(playerId, donate);
        int index = values.indexOf(oldFilter);
        if (index >= 0) values.set(index, newFilter);
    }

    private List<FavoriteFilter> mutableList(UUID playerId, boolean donate) {
        return favorites.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(donate, ignored -> new ArrayList<>());
    }

    private void load() {
        favorites.clear();
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidValue : players.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(uuidValue);
                loadAuction(players.getConfigurationSection(uuidValue + ".vault"), playerId, false);
                loadAuction(players.getConfigurationSection(uuidValue + ".donate"), playerId, true);
            } catch (IllegalArgumentException ignored) {
                // Invalid player entries are ignored.
            }
        }
    }

    private void loadAuction(ConfigurationSection section, UUID playerId, boolean donate) {
        if (section == null) return;
        List<FavoriteFilter> values = mutableList(playerId, donate);
        for (String id : section.getKeys(false)) {
            String typeValue = section.getString(id + ".type", "");
            String value = section.getString(id + ".value", "");
            try {
                FavoriteFilter.Type type = FavoriteFilter.Type.valueOf(typeValue.toUpperCase(Locale.ROOT));
                if (!value.isBlank()) values.add(new FavoriteFilter(id, type, value,
                        section.getDouble(id + ".maximum-price", 0),
                        section.getString(id + ".enchantment", ""),
                        section.getInt(id + ".enchantment-level", 0)));
            } catch (IllegalArgumentException ignored) {
                // Invalid filters do not prevent the plugin from starting.
            }
        }
        mergeDuplicatePriceFilters(values);
    }

    private void mergeDuplicatePriceFilters(List<FavoriteFilter> values) {
        Map<String, FavoriteFilter> merged = new LinkedHashMap<>();
        List<FavoriteFilter> result = new ArrayList<>();
        for (FavoriteFilter filter : values) {
            if (filter.type() != FavoriteFilter.Type.PRICE) {
                result.add(filter);
                continue;
            }
            String key = filter.value().toLowerCase(Locale.ROOT);
            FavoriteFilter current = merged.get(key);
            if (current == null) {
                merged.put(key, filter);
                continue;
            }
            Map<String, Integer> conditions = new LinkedHashMap<>(current.enchantments());
            filter.enchantments().forEach((enchantment, level) ->
                    conditions.merge(enchantment, level, Math::max));
            double price = current.maximumPrice() > 0 && filter.maximumPrice() > 0
                    ? Math.min(current.maximumPrice(), filter.maximumPrice())
                    : Math.max(current.maximumPrice(), filter.maximumPrice());
            merged.put(key, new FavoriteFilter(current.id(), current.type(), current.value(), price,
                    current.enchantment(), current.enchantmentLevel()).withEnchantments(conditions));
        }
        result.addAll(merged.values());
        values.clear();
        values.addAll(result);
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        favorites.forEach((playerId, auctions) -> auctions.forEach((donate, filters) -> {
            String auction = donate ? "donate" : "vault";
            for (FavoriteFilter filter : filters) {
                String path = "players." + playerId + "." + auction + "." + filter.id();
                yaml.set(path + ".type", filter.type().name());
                yaml.set(path + ".value", filter.value());
                if (filter.type() == FavoriteFilter.Type.PRICE) {
                    yaml.set(path + ".maximum-price", filter.maximumPrice());
                    if (filter.hasEnchantment()) {
                        yaml.set(path + ".enchantment", filter.enchantment());
                        yaml.set(path + ".enchantment-level", filter.enchantmentLevel());
                    }
                }
            }
        }));
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("cannot create plugin data folder");
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось сохранить favorites.yml: " + exception.getMessage());
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s{2,}", " ");
    }

    public enum AddResult {
        ADDED, UPDATED, DUPLICATE, LIMIT, INVALID
    }
}
