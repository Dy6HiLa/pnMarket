package ru.privatenull;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceLocalizationTest {
    @Test
    void russianItemLocaleIsValidAndPopulated() {
        var stream = getClass().getClassLoader().getResourceAsStream("lang/ru_ru.json");
        assertNotNull(stream);
        JsonElement root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertTrue(root.isJsonObject());
        assertTrue(root.getAsJsonObject().size() > 500);
    }

    @Test
    void messagesResourceContainsRequiredSections() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream("messages.yml");
        assertNotNull(stream);
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("command:"));
        assertTrue(content.contains("error:"));
        assertTrue(content.contains("listing-unavailable:"));
    }
}
