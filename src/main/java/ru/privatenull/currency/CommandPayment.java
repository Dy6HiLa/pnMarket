package ru.privatenull.currency;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.privatenull.PnMarketPlugin;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Uses PlaceholderAPI for balance checks and console commands for mutations. */
public final class CommandPayment implements MarketPayment {
    private final CurrencyDefinition definition;
    private static final Pattern NUMERIC = Pattern.compile("[0-9]+(?:[.,][0-9]+)?");

    public CommandPayment(PnMarketPlugin plugin, CurrencyDefinition definition) {
        this.definition = definition;
    }

    @Override public boolean isAvailable() {
        return !definition.balancePlaceholder().isBlank()
                && !definition.withdrawCommand().isBlank()
                && !definition.depositCommand().isBlank();
    }
    @Override public boolean has(Player player, double amount) {
        Double balance = balance(player);
        return validAmount(amount) && isAvailable() && balance != null && balance >= amount;
    }
    @Override public boolean withdraw(Player player, double amount) {
        return has(player, amount) && dispatch(definition.withdrawCommand(), player, amount);
    }
    @Override public boolean withdraw(OfflinePlayer player, double amount) {
        return isAvailable() && validAmount(amount) && dispatch(definition.withdrawCommand(), player, amount);
    }
    @Override public boolean deposit(OfflinePlayer player, double amount) {
        return isAvailable() && validAmount(amount) && dispatch(definition.depositCommand(), player, amount);
    }
    @Override public String format(double amount) {
        return validAmount(amount) ? BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString() : "0";
    }
    @Override public String suffix() { return definition.name(); }

    private Double balance(Player player) {
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Object value = papi.getMethod("setPlaceholders", OfflinePlayer.class, String.class)
                    .invoke(null, player, definition.balancePlaceholder());
            String numeric = String.valueOf(value).trim();
            if (!NUMERIC.matcher(numeric).matches()) return null;
            double parsed = Double.parseDouble(numeric.replace(',', '.'));
            return Double.isFinite(parsed) && parsed >= 0 ? parsed : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private boolean dispatch(String template, OfflinePlayer player, double amount) {
        if (template.isBlank() || player == null || player.getUniqueId() == null || !validAmount(amount)) return false;
        String playerName = player.getName();
        if (playerName == null || playerName.isBlank()) return false;
        String command = template.replace("{player}", player.getName() == null ? "" : player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{amount}", format(amount));
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean validAmount(double amount) {
        return Double.isFinite(amount) && amount > 0;
    }
}
