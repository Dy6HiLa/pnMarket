package ru.privatenull.localization;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

public final class ItemLocalization {
    private ItemLocalization() {
    }

    public static Component getNameComponent(ItemStack stack) {
        return Component.text(getPlainName(stack));
    }

    public static String getPlainName(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            String fallback = LangRu.tr("pnmarket.item.fallback");
            return fallback == null ? "Предмет" : fallback;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = ChatColor.stripColor(meta.getDisplayName()).trim();
            if (!name.isEmpty()) return name;
        }

        return getMaterialName(stack.getType());
    }

    public static String getMaterialName(Material material) {
        if (material == null || material.isAir()) {
            String fallback = LangRu.tr("pnmarket.item.fallback");
            return fallback == null ? "Предмет" : fallback;
        }
        String key = "item.minecraft." + material.name().toLowerCase(Locale.ROOT);
        String localized = LangRu.tr(key);
        if ((localized == null || localized.isBlank()) && material.isBlock()) {
            localized = LangRu.tr("block.minecraft." + material.name().toLowerCase(Locale.ROOT));
        }
        if (localized != null && !localized.isBlank()) return localized;

        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    public static Material matchMaterial(String value) {
        if (value == null || value.isBlank()) return null;
        Material direct = Material.matchMaterial(value.trim());
        if (direct != null && !direct.isAir()) return direct;

        String normalized = normalize(value);
        for (Material material : Material.values()) {
            if (!material.isAir() && normalize(getMaterialName(material)).equals(normalized)) {
                return material;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s{2,}", " ");
    }
}
