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
            return fallback == null ? "Item" : fallback;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = ChatColor.stripColor(meta.getDisplayName()).trim();
            if (!name.isEmpty()) return name;
        }

        Material material = stack.getType();
        String key = "item.minecraft." + material.name().toLowerCase(Locale.ROOT);
        String localized = LangRu.tr(key);
        if ((localized == null || localized.isBlank()) && material.isBlock()) {
            localized = LangRu.tr("block.minecraft." + material.name().toLowerCase(Locale.ROOT));
        }
        if (localized != null && !localized.isBlank()) return localized;

        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
