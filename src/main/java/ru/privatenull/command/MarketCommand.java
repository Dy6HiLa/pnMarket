package ru.privatenull.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.market.MarketSearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MarketCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("sell", "search", "show", "reload");
    private final PnMarketPlugin plugin;

    public MarketCommand(PnMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().message("command.only-player"));
            return true;
        }
        if (args.length == 0) {
            plugin.openAuction(player);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(player);
            case "sell" -> sell(player, args);
            case "search" -> search(player, args);
            case "show", "snow" -> show(player, args);
            default -> false;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("search")) {
            return MarketSearch.tabComplete(plugin.activeListings(), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("show") || args[0].equalsIgnoreCase("snow"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) names.add(online.getName());
            }
            return names;
        }
        return List.of();
    }

    private boolean reload(Player player) {
        if (!player.hasPermission("pnmarket.admin")) {
            player.sendMessage(plugin.messages().message("command.no-permission"));
            return true;
        }
        plugin.reloadRuntime();
        player.sendMessage(plugin.messages().message("command.reloaded"));
        return true;
    }

    private boolean sell(Player player, String[] args) {
        if (args.length < 2) {
            reject(player, "command.sell-usage");
            return true;
        }
        plugin.sell(player, args[1]);
        return true;
    }

    private boolean search(Player player, String[] args) {
        if (args.length < 2) {
            reject(player, "command.search-usage");
            return true;
        }
        String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (query.isEmpty()) {
            reject(player, "command.search-usage");
            return true;
        }
        plugin.openAuctionSearch(player, query);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
        return true;
    }

    private boolean show(Player player, String[] args) {
        if (args.length < 2) {
            reject(player, "command.seller-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            reject(player, "error.player-not-found");
            return true;
        }
        plugin.openSellerGui(player, target.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
        return true;
    }

    private void reject(Player player, String messageKey) {
        player.sendMessage(plugin.messages().message(messageKey));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
    }
}
