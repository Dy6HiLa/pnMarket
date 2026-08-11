package ru.privatenull;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ru.privatenull.currency.CurrencyDefinition;

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
        var messages = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertNotNull(messages.getString("prefix"));
        assertNotNull(messages.getString("error.listing-unavailable"));
        assertTrue(messages.getString("notification.favorite-listing", "").contains("{prefix}"));
        assertTrue(messages.getString("notification.favorite-listing", "").contains("{item}"));
        assertNotNull(messages.getString("gui.favorites.entry-info-title"));
        assertNotNull(messages.getString("gui.favorites.help-material"));
        assertNotNull(messages.getString("favorites.filter.material"));
    }

    @Test
    void configContainsVersion102FeatureSettings() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        var config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertTrue(config.getInt("kits.max-slots.default") > 0);
        assertTrue(config.getInt("kits.max-slots.vip") > config.getInt("kits.max-slots.default"));
        assertTrue(config.getInt("kits.max-serialized-bytes") >= 4096);
        assertTrue(!config.getStringList("kits.blocked-materials").isEmpty());
        assertTrue(config.getBoolean("price-statistics.enabled"));
        assertTrue(config.getInt("notifications.max-favorites") > 0);
        assertTrue(config.isConfigurationSection("categories.bundles"));
    }

    @Test
    void configContainsCurrencyDefinitions() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        var config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertTrue(config.isConfigurationSection("currencies.vault"));
        assertTrue(config.getBoolean("currencies.vault.enabled"));
        assertNotNull(config.getString("currencies.vault.amount-placeholder"));
        assertTrue(config.isConfigurationSection("currencies.playerpoints"));
        assertTrue(config.getString("currencies.playerpoints.withdraw-command", "")
                .contains("{amount}"));
        assertTrue(config.getString("currencies.playerpoints.deposit-command", "")
                .contains("{player}"));
        assertTrue(!config.getBoolean("currencies.tokens.enabled"));
    }

    @Test
    void commandCurrencyRejectsUnknownAndMalformedPlaceholders() {
        assertThrows(IllegalArgumentException.class, () -> new CurrencyDefinition(
                "tokens", "Tokens", "%tokens_balance%", "tokens take {player} {balance}", ""));
        assertThrows(IllegalArgumentException.class, () -> new CurrencyDefinition(
                "tokens", "Tokens", "%tokens_balance%", "tokens take {player} {amount", ""));
    }
}
