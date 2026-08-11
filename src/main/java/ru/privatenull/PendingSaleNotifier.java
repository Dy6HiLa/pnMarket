package ru.privatenull;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.localization.ItemLocalization;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persists sale messages so sellers do not miss purchases made while offline. */
final class PendingSaleNotifier implements Listener {
    private final PnMarketPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    PendingSaleNotifier(PnMarketPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-sales.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    synchronized void queue(UUID seller, String buyer, ItemStack item, double amount, boolean donate) {
        String path = seller + ".sales";
        List<String> sales = new ArrayList<>(data.getStringList(path));
        sales.add(buyer + "|" + ItemLocalization.getPlainName(item) + "|" + amount + "|" + donate);
        data.set(path, sales);
        save();
    }

    @EventHandler
    public synchronized void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String path = player.getUniqueId() + ".sales";
        List<String> sales = data.getStringList(path);
        if (sales.isEmpty()) return;
        for (String sale : sales) {
            String[] parts = sale.split("\\|", 4);
            if (parts.length < 4) continue;
            String currency = Boolean.parseBoolean(parts[3]) ? " PP" : "⛃";
            player.sendMessage("§a[Аукцион] §7» §fПока вас не было, игрок §e@" + parts[0]
                    + " §fкупил §e" + parts[1] + " §fза §a" + parts[2] + currency + "§f.");
        }
        data.set(path, null);
        save();
    }

    private void save() {
        try {
            file.getParentFile().mkdirs();
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось сохранить уведомления о продажах: " + exception.getMessage());
        }
    }
}
