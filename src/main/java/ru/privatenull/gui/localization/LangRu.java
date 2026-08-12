package ru.privatenull.localization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class LangRu {

    private static final Map<String, String> MAP = new HashMap<>();

    public static void init(JavaPlugin plugin) {
        MAP.clear();
        Path localeFile = plugin.getDataFolder().toPath().resolve("lang/ru_ru.json");
        try (InputStream resource = plugin.getResource("lang/ru_ru.json")) {
            if (resource != null) load(new InputStreamReader(resource, StandardCharsets.UTF_8));
        } catch (IOException | com.google.gson.JsonParseException ex) {
            plugin.getLogger().warning("[LangRu] Failed to load bundled ru_ru.json: " + ex.getMessage());
        }
        if (Files.notExists(localeFile)) plugin.saveResource("lang/ru_ru.json", false);
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(localeFile), StandardCharsets.UTF_8)) {
            load(reader);
            plugin.getLogger().info("[LangRu] Loaded " + MAP.size() + " ru_ru entries");
        } catch (IOException | com.google.gson.JsonParseException ex) {
            plugin.getLogger().warning("[LangRu] Failed to load ru_ru.json: " + ex.getMessage());
        }
    }

    public static String tr(String key) {
        if (key == null) return null;
        return MAP.get(key);
    }

    private static void load(InputStreamReader reader) {
        JsonElement root = new JsonParser().parse(reader);
        if (!root.isJsonObject()) throw new com.google.gson.JsonParseException("Locale root is not a JsonObject");
        JsonObject obj = root.getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (e.getValue().isJsonPrimitive()) MAP.put(e.getKey(), e.getValue().getAsString());
        }
    }
}
