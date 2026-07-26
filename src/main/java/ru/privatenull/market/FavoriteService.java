package ru.privatenull.market;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.localization.ItemLocalization;
import ru.privatenull.model.MarketListing;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
        return List.copyOf(favorites
                .getOrDefault(playerId, Map.of())
                .getOrDefault(donate, List.of()));
    }

    public synchronized AddResult addMaterial(UUID playerId, boolean donate, String rawMaterial) {
        Material material = ItemLocalization.matchMaterial(rawMaterial);
        if (material == null || material.isAir()) return AddResult.INVALID;
        return add(playerId, donate, FavoriteFilter.Type.MATERIAL, material.name());
    }

    public synchronized AddResult addName(UUID playerId, boolean donate, String rawName) {
        String value = normalize(rawName);
        if (value.length() < 2 || value.length() > 32) return AddResult.INVALID;
        return add(playerId, donate, FavoriteFilter.Type.NAME, value);
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

    public void notifyListing(MarketListing listing, boolean donate) {
        if (!plugin.getConfig().getBoolean("notifications.enabled", true)) return;
        ItemStack item = listing.item();
        String itemName = ItemLocalization.getPlainName(item);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(listing.sellerId())) continue;
            FavoriteFilter matched = firstMatch(player.getUniqueId(), donate, item, itemName);
            if (matched == null) continue;
            String root = donate ? "/dah" : "/ah";
            player.sendMessage(plugin.messages().message("notification.favorite-listing", Map.of(
                    "item", itemName,
                    "filter", filterLabel(matched),
                    "command", root + " search " + itemName
            )));
        }
    }

    public String filterLabel(FavoriteFilter filter) {
        return filter.type() == FavoriteFilter.Type.MATERIAL
                ? plugin.messages().message("favorites.filter.material", Map.of(
                "value", displayValue(filter)))
                : plugin.messages().message("favorites.filter.name", Map.of(
                "value", displayValue(filter)));
    }

    public String displayValue(FavoriteFilter filter) {
        if (filter.type() != FavoriteFilter.Type.MATERIAL) return filter.value();
        Material material = Material.matchMaterial(filter.value());
        return material == null ? filter.value() : ItemLocalization.getMaterialName(material);
    }

    private synchronized FavoriteFilter firstMatch(UUID playerId, boolean donate, ItemStack item, String itemName) {
        String normalizedName = normalize(itemName);
        for (FavoriteFilter filter : list(playerId, donate)) {
            if (filter.type() == FavoriteFilter.Type.MATERIAL
                    && item.getType().name().equalsIgnoreCase(filter.value())) return filter;
            if (filter.type() == FavoriteFilter.Type.NAME
                    && normalizedName.contains(normalize(filter.value()))) return filter;
        }
        return null;
    }

    private AddResult add(UUID playerId, boolean donate, FavoriteFilter.Type type, String value) {
        List<FavoriteFilter> values = mutableList(playerId, donate);
        if (values.stream().anyMatch(filter -> filter.type() == type
                && filter.value().equalsIgnoreCase(value))) return AddResult.DUPLICATE;
        int maximum = Math.max(1, plugin.getConfig().getInt("notifications.max-favorites", 10));
        if (values.size() >= maximum) return AddResult.LIMIT;
        values.add(new FavoriteFilter(UUID.randomUUID().toString(), type, value));
        save();
        return AddResult.ADDED;
    }

    private List<FavoriteFilter> mutableList(UUID playerId, boolean donate) {
        return favorites
                .computeIfAbsent(playerId, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(donate, ignored -> new ArrayList<>());
    }

    private void load() {
        favorites.clear();
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidValue : players.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(uuidValue);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            loadAuction(players.getConfigurationSection(uuidValue + ".vault"), playerId, false);
            loadAuction(players.getConfigurationSection(uuidValue + ".donate"), playerId, true);
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
                if (!value.isBlank()) values.add(new FavoriteFilter(id, type, value));
            } catch (IllegalArgumentException ignored) {
                // Invalid entries are skipped instead of disabling the plugin.
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<Boolean, List<FavoriteFilter>>> playerEntry : favorites.entrySet()) {
            for (Map.Entry<Boolean, List<FavoriteFilter>> auctionEntry : playerEntry.getValue().entrySet()) {
                String auction = auctionEntry.getKey() ? "donate" : "vault";
                for (FavoriteFilter filter : auctionEntry.getValue()) {
                    String path = "players." + playerEntry.getKey() + "." + auction + "." + filter.id();
                    yaml.set(path + ".type", filter.type().name());
                    yaml.set(path + ".value", filter.value());
                }
            }
        }
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
        ADDED,
        DUPLICATE,
        LIMIT,
        INVALID
    }
}
