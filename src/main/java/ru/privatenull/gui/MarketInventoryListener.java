package ru.privatenull.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import ru.privatenull.PnMarketPlugin;

public final class MarketInventoryListener implements Listener {
    private final PnMarketPlugin plugin;

    public MarketInventoryListener(PnMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        if (top.getHolder() instanceof MyItemsView view) {
            event.setCancelled(true);
            if (clicked.equals(top)) plugin.gui().handleMyItemsClick(player, view, event.getRawSlot());
            return;
        }
        if (top.getHolder() instanceof SellerView view) {
            event.setCancelled(true);
            if (clicked.equals(top)) {
                plugin.gui().handleSellerClick(player, view, event.getRawSlot(), event.isLeftClick(), event.isRightClick());
            }
            return;
        }
        if (top.getHolder() instanceof PurchaseView view) {
            event.setCancelled(true);
            if (clicked.equals(top)) plugin.gui().handlePurchaseClick(player, view, event.getRawSlot());
            return;
        }
        if (top.getHolder() instanceof AuctionView view) {
            event.setCancelled(true);
            if (!player.getUniqueId().equals(view.viewer) || !clicked.equals(top)) return;
            plugin.gui().handleAuctionClick(player, view, event.getRawSlot(), event.isLeftClick(), event.isRightClick());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        plugin.gui().closeView(event.getPlayer().getUniqueId(), event.getInventory());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.gui().removeViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.notifyUpdate(event.getPlayer());
    }
}
