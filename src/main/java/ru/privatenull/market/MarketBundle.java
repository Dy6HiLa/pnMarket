package ru.privatenull.market;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Stores bundle contents separately from the version-specific visual item. */
public final class MarketBundle {
    private static final String BUNDLE_KEY = "market_bundle";
    private static final String CONTENTS_KEY = "market_bundle_contents";

    private MarketBundle() {
    }

    public static ItemStack create(Plugin plugin, List<ItemStack> contents) {
        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException("A bundle must contain at least one item");
        }

        int count = count(contents);
        ItemStack bundle = new ItemStack(visualMaterial(count));
        ItemMeta meta = bundle.getItemMeta();
        if (meta == null) throw new IllegalStateException("Bundle item meta is unavailable");
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(contentsKey(plugin), PersistentDataType.BYTE_ARRAY, encode(contents));
        meta.setDisplayName("§6Набор");
        try {
            meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (IllegalArgumentException ignored) {
            // The flag does not exist on legacy server versions.
        }
        bundle.setItemMeta(meta);
        return bundle;
    }

    public static boolean isBundle(Plugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte marker = meta.getPersistentDataContainer().get(key(plugin), PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public static List<ItemStack> contents(Plugin plugin, ItemStack bundle) {
        if (bundle == null || bundle.getType().isAir()) return List.of();
        ItemMeta itemMeta = bundle.getItemMeta();
        if (itemMeta != null) {
            byte[] serialized = itemMeta.getPersistentDataContainer().get(contentsKey(plugin), PersistentDataType.BYTE_ARRAY);
            if (serialized != null) return decode(serialized);
        }

        // Compatibility with the first release of kits, where contents were in a shulker state.
        if (!(itemMeta instanceof BlockStateMeta meta)) return List.of();
        if (!(meta.getBlockState() instanceof ShulkerBox shulker)) return List.of();
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : shulker.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) result.add(item.clone());
        }
        return result;
    }

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, BUNDLE_KEY);
    }

    private static NamespacedKey contentsKey(Plugin plugin) {
        return new NamespacedKey(plugin, CONTENTS_KEY);
    }

    private static Material visualMaterial(int itemCount) {
        int minor = minecraftMinorVersion();
        if (minor >= 20) {
            if (itemCount <= 4) return materialFirst("BROWN_BUNDLE", "BUNDLE", "SHULKER_BOX");
            if (itemCount <= 9) return materialFirst("ORANGE_BUNDLE", "BUNDLE", "SHULKER_BOX");
            if (itemCount <= 14) return materialFirst("YELLOW_BUNDLE", "BUNDLE", "SHULKER_BOX");
            return materialFirst("RED_BUNDLE", "BUNDLE", "SHULKER_BOX");
        }
        if (minor >= 17) return Material.SHULKER_BOX;
        return Material.BARREL;
    }

    private static Material materialFirst(String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) return material;
        }
        return Material.BARREL;
    }

    private static int minecraftMinorVersion() {
        try {
            String version = Bukkit.getBukkitVersion().split("-", 2)[0];
            String[] parts = version.split("\\.");
            return parts.length >= 2 ? Integer.parseInt(parts[1]) : 16;
        } catch (RuntimeException ignored) {
            return 16;
        }
    }

    private static byte[] encode(List<ItemStack> contents) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             BukkitObjectOutputStream data = new BukkitObjectOutputStream(output)) {
            List<ItemStack> items = contents.stream()
                    .filter(item -> item != null && !item.getType().isAir())
                    .map(ItemStack::clone)
                    .toList();
            data.writeInt(items.size());
            for (ItemStack item : items) data.writeObject(item);
            data.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot serialize bundle contents", exception);
        }
    }

    private static List<ItemStack> decode(byte[] serialized) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(serialized);
             BukkitObjectInputStream data = new BukkitObjectInputStream(input)) {
            int size = data.readInt();
            if (size < 1 || size > 36) return List.of();
            List<ItemStack> result = new ArrayList<>();
            for (int index = 0; index < size; index++) {
                Object value = data.readObject();
                if (value instanceof ItemStack item && !item.getType().isAir()) result.add(item);
            }
            return result;
        } catch (IOException | ClassNotFoundException exception) {
            return List.of();
        }
    }

    private static int count(List<ItemStack> items) {
        return (int) items.stream().filter(item -> item != null && !item.getType().isAir()).count();
    }
}
