package ru.privatenull.gui;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class NotificationCatalogView implements InventoryHolder {
    MarketGuiController controller;
    Inventory inventory;
    int page;
    String category;
    Mode mode;
    Material selectedMaterial;
    String selectedItemKey;
    Map<Integer, String> slotToItemKey;
    Map<Integer, String> slotToCategory;
    Map<Integer, Enchantment> slotToEnchantment;

    enum Mode {
        ITEMS, CATEGORIES, ENCHANTMENTS
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
