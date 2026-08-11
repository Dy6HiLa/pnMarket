package ru.privatenull.currency;

import org.bukkit.configuration.ConfigurationSection;
import ru.privatenull.PnMarketPlugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Loads and validates currencies without linking pnMarket to PlaceholderAPI. */
public final class CurrencyRegistry {
    private CurrencyRegistry() { }

    public static Map<String, CurrencyDefinition> load(PnMarketPlugin plugin, Logger logger) {
        Map<String, CurrencyDefinition> result = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("currencies");
        if (section == null) throw new IllegalArgumentException("currencies section is missing");
        for (String rawId : section.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            if (!section.getBoolean(rawId + ".enabled", true)) continue;
            try {
                CurrencyDefinition definition = new CurrencyDefinition(id,
                        section.getString(rawId + ".name", id),
                        section.getString(rawId + ".amount-placeholder", ""),
                        section.getString(rawId + ".withdraw-command", ""),
                        section.getString(rawId + ".deposit-command", ""));
                if (result.putIfAbsent(id, definition) != null) {
                    logger.warning("Duplicate currency id " + rawId + "; currency disabled. Support: "
                            + PnMarketPlugin.SUPPORT_DISCORD);
                }
            } catch (IllegalArgumentException exception) {
                logger.warning("Invalid currency " + rawId + ": " + exception.getMessage()
                        + ". Currency disabled. Support: " + PnMarketPlugin.SUPPORT_DISCORD);
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("No enabled currencies configured");
        return result;
    }
}
