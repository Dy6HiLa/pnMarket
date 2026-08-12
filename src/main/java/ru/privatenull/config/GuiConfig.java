package ru.privatenull.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.format.TextDecoration;
import ru.privatenull.pnlibrary.item.HeadUtil;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.io.File;
import java.util.*;

public final class GuiConfig {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public GuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gui.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) plugin.saveResource("gui.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration configuration() {
        return config;
    }

    public void set(String path, Object value) {
        config.set(path, value);
    }

    public boolean save() {
        try {
            config.save(file);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().warning("Не удалось сохранить gui.yml: " + exception.getMessage());
            return false;
        }
    }

    public ItemStack item(String path, Material fallback, Map<String, ?> placeholders) {
        String name = text(path + ".name", placeholders);
        String materialValue = config.getString(path + ".material", fallback.name()).trim();
        String base64 = config.getString(path + ".base64", "").trim();
        if (base64.isEmpty() && (materialValue.toLowerCase(Locale.ROOT).startsWith("base64:")
                || materialValue.toLowerCase(Locale.ROOT).startsWith("base64-"))) {
            base64 = materialValue.substring(7).trim();
        }
        ItemStack item;
        if (!base64.isEmpty()) {
            item = HeadUtil.create(base64, name);
        } else {
            Material material = Material.matchMaterial(materialValue.toUpperCase(Locale.ROOT));
            item = new ItemStack(material == null ? fallback : material);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.component(name).decoration(TextDecoration.ITALIC, false));
            meta.setLore(lore(path + ".lore", placeholders));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public String text(String path) {
        return text(path, Map.of());
    }

    public String text(String path, Map<String, ?> placeholders) {
        String value = config.getString(path);
        if (value == null) {
            plugin.getLogger().warning("Отсутствует строка gui.yml: " + path);
            value = "&cMissing GUI text: " + path;
        }
        return ColorUtil.colorize(replace(value, placeholders));
    }

    public List<String> lore(String path) {
        return lore(path, Map.of());
    }

    public List<String> lore(String path, Map<String, ?> placeholders) {
        return config.getStringList(path).stream()
                .map(line -> ColorUtil.colorize(replace(line, placeholders)))
                .toList();
    }

    private String replace(String value, Map<String, ?> placeholders) {
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return value;
    }
}
