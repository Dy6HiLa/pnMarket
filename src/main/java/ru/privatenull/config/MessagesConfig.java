package ru.privatenull.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class MessagesConfig {
    private final JavaPlugin plugin;
    private YamlConfiguration config;

    public MessagesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String message(String key) {
        return message(key, Map.of());
    }

    public String message(String key, Map<String, ?> placeholders) {
        String value = config.getString(key);
        if (value == null) {
            plugin.getLogger().warning("Отсутствует строка локализации messages.yml: " + key);
            value = "&c[pnMarket] &fMissing message: " + key;
        }
        String prefix = config.getString("prefix", "&x&6&8&F&B&3&C[Аукцион] &7»");
        value = value.replace("{prefix}", prefix);
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
