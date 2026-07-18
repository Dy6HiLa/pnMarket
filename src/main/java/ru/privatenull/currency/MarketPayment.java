package ru.privatenull.currency;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public interface MarketPayment {
    boolean isAvailable();

    boolean has(Player player, double amount);

    boolean withdraw(Player player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    String format(double amount);

    String suffix();
}
