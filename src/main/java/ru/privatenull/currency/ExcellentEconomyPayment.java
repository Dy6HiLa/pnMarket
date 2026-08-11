package ru.privatenull.currency;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/** Optional native ExcellentEconomy adapter. It is deliberately reflection-based. */
public final class ExcellentEconomyPayment implements MarketPayment {
    private final Object api;
    private final String currencyId;
    private final Method getBalance;
    private final Method withdraw;
    private final Method deposit;

    public ExcellentEconomyPayment(String currencyId) {
        this.currencyId = currencyId;
        Object found = null;
        Method balance = null;
        Method take = null;
        Method give = null;
        try {
            Class<?> apiType = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI");
            var registration = Bukkit.getServicesManager().getRegistration(apiType);
            found = registration == null ? null : registration.getProvider();
            if (found != null) {
                balance = apiType.getMethod("getBalance", Player.class, String.class);
                take = apiType.getMethod("withdraw", Player.class, String.class, double.class);
                give = apiType.getMethod("deposit", Player.class, String.class, double.class);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Optional dependency is not installed or exposes another API version.
        }
        this.api = found;
        this.getBalance = balance;
        this.withdraw = take;
        this.deposit = give;
    }

    @Override public boolean isAvailable() { return api != null && getBalance != null && withdraw != null && deposit != null; }

    @Override public boolean has(Player player, double amount) {
        if (!isAvailable() || !validAmount(amount)) return false;
        try { return ((Number) getBalance.invoke(api, player, currencyId)).doubleValue() >= amount; }
        catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    @Override public boolean withdraw(Player player, double amount) {
        return invoke(withdraw, player, amount);
    }

    @Override public boolean withdraw(OfflinePlayer player, double amount) {
        return player.isOnline() && withdraw(player.getPlayer(), amount);
    }

    @Override public boolean deposit(OfflinePlayer player, double amount) {
        return player.isOnline() && invoke(deposit, player.getPlayer(), amount);
    }

    @Override public String format(double amount) { return String.format(java.util.Locale.ROOT, "%,.2f", amount); }
    @Override public String suffix() { return currencyId; }

    private boolean invoke(Method method, Player player, double amount) {
        if (!isAvailable() || !validAmount(amount)) return false;
        try {
            Object result = method.invoke(api, player, currencyId, amount);
            return result instanceof Boolean value && value;
        }
        catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }
    private boolean validAmount(double amount) { return Double.isFinite(amount) && amount > 0; }
}
