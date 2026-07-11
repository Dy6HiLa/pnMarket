package ru.privatenull.update;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.config.MessagesConfig;

import java.util.Map;

public final class UpdateChecker {
    private final PnMarketPlugin plugin;
    private final MessagesConfig messages;
    private BukkitTask task;
    private volatile UpdateInfo available;

    public UpdateChecker(PnMarketPlugin plugin, MessagesConfig messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void start() {
        String repository = "Dy6HiLa/pnMarket";
        long minutes = 360L;
        GitHubUpdateClient client;
        try {
            client = new GitHubUpdateClient(repository);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Проверка обновлений отключена: " + exception.getMessage());
            return;
        }
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> check(client), 100L, minutes * 1200L);
    }

    public void cancel() {
        if (task != null) task.cancel();
        task = null;
    }

    public void notifyOnJoin(Player player) {
        if (available != null && player.hasPermission("pnmarket.admin")) notifyPlayer(player, available);
    }

    private void check(GitHubUpdateClient client) {
        try {
            UpdateInfo latest = client.fetchLatest();
            if (latest.version() != null && VersionComparator.compare(latest.version(), plugin.getDescription().getVersion()) > 0) {
                boolean changed = available == null || !available.version().equals(latest.version());
                available = latest;
                if (changed) {
                    plugin.getLogger().warning("Доступна новая версия pnMarket " + latest.version() + ": " + latest.downloadUrl());
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::notifyOnJoin));
                }
            } else {
                available = null;
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Не удалось проверить обновления pnMarket: " + exception.getMessage());
        }
    }

    private void notifyPlayer(Player player, UpdateInfo info) {
        Component message = LegacyComponentSerializer.legacySection().deserialize(
                messages.message("update.available", Map.of("version", info.version()))
        );
        player.sendMessage(message
                .clickEvent(ClickEvent.openUrl(info.downloadUrl())));
    }
}
