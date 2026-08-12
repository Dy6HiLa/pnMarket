package ru.privatenull.localization;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

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
        if (meta instanceof PotionMeta potionMeta) return getPotionName(stack.getType(), potionMeta.getBasePotionData());

        return getMaterialName(stack.getType());
    }

    public static String getItemKey(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return Material.AIR.name();
        if (stack.getItemMeta() instanceof PotionMeta potionMeta) {
            PotionData data = potionMeta.getBasePotionData();
            return stack.getType().name() + ":" + data.getType().name() + ":"
                    + data.isExtended() + ":" + data.isUpgraded();
        }
        return stack.getType().name();
    }

    public static Material getKeyMaterial(String key) {
        if (key == null || key.isBlank()) return null;
        return Material.matchMaterial(key.split(":", 2)[0]);
    }

    public static ItemStack createItem(String key) {
        Material material = getKeyMaterial(key);
        if (material == null || material.isAir()) return new ItemStack(Material.PAPER);
        ItemStack item = new ItemStack(material);
        String[] parts = key.split(":");
        if (parts.length == 4 && item.getItemMeta() instanceof PotionMeta meta) {
            try {
                meta.setBasePotionData(new PotionData(PotionType.valueOf(parts[1]),
                        Boolean.parseBoolean(parts[2]), Boolean.parseBoolean(parts[3])));
                item.setItemMeta(meta);
            } catch (IllegalArgumentException ignored) {
                // The material itself is still a safe fallback for outdated saved variants.
            }
        }
        return item;
    }

    public static String getItemName(String key) {
        return getPlainName(createItem(key));
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

        return readableRussianFallback(material.name());
    }

    public static String getEnchantmentName(Enchantment enchantment) {
        if (enchantment == null) return "Зачарование";
        String path = enchantment.getKey().getKey();
        String localized = LangRu.tr("enchantment.minecraft." + path);
        return localized == null || localized.isBlank()
                ? "Неизвестное зачарование" : localized;
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

    private static String readableRussianFallback(String value) {
        String raw = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return "Неизвестный предмет («" + raw + "»)";
    }

    private static String getPotionName(Material material, PotionData data) {
        String prefix = switch (material.name()) {
            case "SPLASH_POTION" -> "splash_potion";
            case "LINGERING_POTION" -> "lingering_potion";
            case "TIPPED_ARROW" -> "tipped_arrow";
            default -> "potion";
        };
        String effect = switch (data.getType().name()) {
            case "JUMP" -> "leaping";
            case "SPEED" -> "swiftness";
            case "INSTANT_HEAL" -> "healing";
            case "INSTANT_DAMAGE" -> "harming";
            case "REGEN" -> "regeneration";
            default -> data.getType().name().toLowerCase(Locale.ROOT);
        };
        String localized = LangRu.tr("item.minecraft." + prefix + ".effect." + effect);
        if (localized == null || localized.isBlank()) localized = getMaterialName(material);
        if (data.isUpgraded()) return localized + " (усиленное)";
        if (data.isExtended()) return localized + " (длительное)";
        return localized;
    }
}
