package ru.privatenull.notification;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.privatenull.PnMarketPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable chat notifications delivered on the player's next join. */
public final class PendingNotificationService {
    private final PnMarketPlugin plugin;
    private final File file;
    private final Map<UUID, List<String>> pending = new LinkedHashMap<>();

    public PendingNotificationService(PnMarketPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-notifications.yml");
        load();
    }

    public synchronized void queue(UUID playerId, String message) {
        if (playerId == null || message == null || message.isBlank()) return;
        int maximum = Math.max(1, plugin.getConfig().getInt("notifications.max-pending", 50));
        List<String> messages = pending.computeIfAbsent(playerId, ignored -> new ArrayList<>());
        if (messages.size() >= maximum) messages.remove(0);
        messages.add(message);
        save();
    }

    public synchronized void deliver(Player player) {
        List<String> messages = pending.remove(player.getUniqueId());
        if (messages == null || messages.isEmpty()) return;
        save();
        player.sendMessage(plugin.messages().message("notification.offline-summary",
                Map.of("amount", messages.size())));
        messages.forEach(player::sendMessage);
    }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                List<String> messages = yaml.getStringList(key);
                if (!messages.isEmpty()) pending.put(id, new ArrayList<>(messages));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        pending.forEach((id, messages) -> yaml.set(id.toString(), messages));
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("cannot create plugin data folder");
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось сохранить отложенные уведомления: " + exception.getMessage());
        }
    }
}
