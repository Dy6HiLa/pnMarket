package ru.privatenull.support.item;

import org.bukkit.inventory.ItemStack;

/**
 * Item comparison that survives skulls whose profile was set without the serialized form: on Paper 1.16.5
 * CraftMetaSkull throws NullPointerException from equals/isSimilar for them.
 */
public final class SimilarItems {
    private SimilarItems() {
    }

    public static boolean isSimilar(ItemStack first, ItemStack second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        try {
            return first.isSimilar(second);
        } catch (NullPointerException brokenSkull) {
            return first.getType() == second.getType() && sameSkullOwner(first, second);
        }
    }

    public static boolean isEqual(ItemStack first, ItemStack second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return isSimilar(first, second) && first.getAmount() == second.getAmount();
    }

    private static boolean sameSkullOwner(ItemStack first, ItemStack second) {
        if (!(first.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta one)
                || !(second.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta two)) {
            return false;
        }
        String left = ownerName(one);
        String right = ownerName(two);
        return left != null && left.equals(right);
    }

    private static String ownerName(org.bukkit.inventory.meta.SkullMeta meta) {
        return meta.getOwningPlayer() == null ? meta.getOwner() : meta.getOwningPlayer().getUniqueId().toString();
    }
}
