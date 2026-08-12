package ru.privatenull.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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

        if (top.getHolder() instanceof BundlePreviewView view) {
            event.setCancelled(true);
            if (clicked.equals(top)) view.controller.handleBundlePreviewClick(player, view, event.getRawSlot());
            return;
        }
        if (top.getHolder() instanceof BundleCreateView view) {
            event.setCancelled(true);
            if (clicked.equals(top)) view.controller.handleBundleCreateClick(player, view, event.getRawSlot());
            return;
        }
        if (top.getHolder() instanceof FavoritesView view) {
            event.setCancelled(true);
            if (clicked.equals(top)) view.controller.handleFavoritesClick(player, view, event.getRawSlot());
            return;
        }
        if (top.getHolder() instanceof NotificationCatalogView view) {
            event.setCancelled(true);
            if (clicked.equals(top)) view.controller.handleNotificationCatalogClick(
                    player, view, event.getRawSlot(), event.isLeftClick(), event.isRightClick());
            return;
        }
        if (top.getHolder() instanceof MyItemsView view) {
            event.setCancelled(true);
            if (clicked.equals(top)) {
                view.controller.handleMyItemsClick(player, view, event.getRawSlot(), event.isRightClick());
            }
            return;
        }
        if (top.getHolder() instanceof SellerView view) {
            event.setCancelled(true);
            if (clicked.equals(top)) {
                view.controller.handleSellerClick(player, view, event.getRawSlot(), event.isLeftClick(), event.isRightClick());
            }
            return;
        }
        if (top.getHolder() instanceof PurchaseView view) {
            event.setCancelled(true);
            if (clicked.equals(top)) view.controller.handlePurchaseClick(player, view, event.getRawSlot());
            return;
        }
        if (top.getHolder() instanceof AuctionView view) {
            event.setCancelled(true);
            if (!player.getUniqueId().equals(view.viewer) || !clicked.equals(top)) return;
            view.controller.handleAuctionClick(player, view, event.getRawSlot(), event.isLeftClick(), event.isRightClick());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!isMarketView(top)) return;
        int topSize = top.getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    private boolean isMarketView(Inventory inventory) {
        Object holder = inventory.getHolder();
        return holder instanceof AuctionView
                || holder instanceof PurchaseView
                || holder instanceof SellerView
                || holder instanceof MyItemsView
                || holder instanceof BundlePreviewView
                || holder instanceof BundleCreateView
                || holder instanceof FavoritesView
                || holder instanceof NotificationCatalogView;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AuctionView view) view.controller.closeView(event.getPlayer().getUniqueId(), event.getInventory());
        else if (event.getInventory().getHolder() instanceof PurchaseView view) view.controller.closeView(event.getPlayer().getUniqueId(), event.getInventory());
        else if (event.getInventory().getHolder() instanceof SellerView view) view.controller.closeView(event.getPlayer().getUniqueId(), event.getInventory());
        else if (event.getInventory().getHolder() instanceof MyItemsView view) view.controller.closeView(event.getPlayer().getUniqueId(), event.getInventory());
        else if (event.getInventory().getHolder() instanceof BundlePreviewView view) view.controller.closeView(event.getPlayer().getUniqueId(), event.getInventory());
        else if (event.getInventory().getHolder() instanceof BundleCreateView view) view.controller.closeView(event.getPlayer().getUniqueId(), event.getInventory());
        else if (event.getInventory().getHolder() instanceof FavoritesView view) view.controller.closeView(event.getPlayer().getUniqueId(), event.getInventory());
        else if (event.getInventory().getHolder() instanceof NotificationCatalogView view) view.controller.closeView(event.getPlayer().getUniqueId(), event.getInventory());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.removeViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.notifyOnJoin(event.getPlayer());
    }
}
