package ru.privatenull.localization;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public class ItemLocalization {

    public static Component getNameComponent(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            String fallback = LangRu.tr("pnmarket.item.fallback");
            return Component.text(fallback == null ? "Item" : fallback);
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            return meta.displayName();
        }

        Material type = stack.getType();
        String key = null;

        if (meta instanceof PotionMeta potionMeta) {
            PotionType potionType = potionMeta.getBasePotionType();
            String potionKey = getPotionTranslationKey(type, potionType);
            if (potionKey != null) {
                String fromRu = LangRu.tr(potionKey);
                if (fromRu != null) {
                    return Component.text(fromRu);
                }
                key = potionKey;
            }
        }

        if (key == null) {
            key = type.translationKey();
        }

        String fromRu = LangRu.tr(key);
        if (fromRu != null) {
            return Component.text(fromRu);
        }

        return Component.translatable(key);
    }

    public static String getPlainName(ItemStack stack) {
        Component c = getNameComponent(stack);
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        if (plain == null) return null;
        plain = plain.trim();
        return plain.isEmpty() ? null : plain;
    }

    private static String getPotionTranslationKey(Material itemType, PotionType type) {
        if (type == null) return null;
        String effectId = getEffectId(type);
        if (effectId == null) return null;
        switch (itemType) {
            case POTION:
                return "item.minecraft.potion.effect." + effectId;
            case SPLASH_POTION:
                return "item.minecraft.splash_potion.effect." + effectId;
            case LINGERING_POTION:
                return "item.minecraft.lingering_potion.effect." + effectId;
            case TIPPED_ARROW:
                return "item.minecraft.tipped_arrow.effect." + effectId;
            default:
                return null;
        }
    }

    private static String getEffectId(PotionType type) {
        switch (type) {
            case SWIFTNESS:
            case LONG_SWIFTNESS:
            case STRONG_SWIFTNESS:
                return "swiftness";
            case SLOWNESS:
            case LONG_SLOWNESS:
            case STRONG_SLOWNESS:
                return "slowness";
            case LEAPING:
            case LONG_LEAPING:
            case STRONG_LEAPING:
                return "leaping";
            case STRENGTH:
            case LONG_STRENGTH:
            case STRONG_STRENGTH:
                return "strength";
            case REGENERATION:
            case LONG_REGENERATION:
            case STRONG_REGENERATION:
                return "regeneration";
            case HEALING:
            case STRONG_HEALING:
                return "healing";
            case HARMING:
            case STRONG_HARMING:
                return "harming";
            case POISON:
            case LONG_POISON:
            case STRONG_POISON:
                return "poison";
            case NIGHT_VISION:
            case LONG_NIGHT_VISION:
                return "night_vision";
            case INVISIBILITY:
            case LONG_INVISIBILITY:
                return "invisibility";
            case FIRE_RESISTANCE:
            case LONG_FIRE_RESISTANCE:
                return "fire_resistance";
            case WATER_BREATHING:
            case LONG_WATER_BREATHING:
                return "water_breathing";
            case LUCK:
                return "luck";
            case SLOW_FALLING:
            case LONG_SLOW_FALLING:
                return "slow_falling";
            case TURTLE_MASTER:
            case LONG_TURTLE_MASTER:
            case STRONG_TURTLE_MASTER:
                return "turtle_master";
            default:
                return null;
        }
    }
}
