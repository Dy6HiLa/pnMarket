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
import java.util.Map;

public final class MarketCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("sell", "kit", "search", "show", "help", "reload");

    private final PnMarketPlugin plugin;
    private final boolean donate;

    public MarketCommand(PnMarketPlugin plugin, boolean donate) {
        this.plugin = plugin;
        this.donate = donate;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().message("command.only-player"));
            return true;
        }
        if (args.length == 0) {
            plugin.openAuction(player, donate);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (donate) {
                    noPermission(player);
                    yield true;
                }
                yield reload(player);
            }
            case "sell" -> sell(player, args);
            case "kit" -> kit(player, args);
            case "search" -> search(player, args);
            case "show", "snow" -> show(player, args);
            case "help", "?" -> help(player);
            default -> help(player);
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(value -> !donate || !value.equals("reload"))
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("search")) {
            return MarketSearch.tabComplete(plugin.activeListings(donate), args[1]);
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
            noPermission(player);
            return true;
        }
        plugin.reloadRuntime();
        player.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§a§lГОТОВО §8• §fКонфиг и аукционы обновлены.");
        player.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return true;
    }

    private boolean sell(Player player, String[] args) {
        if (args.length < 2) {
            usage(player, donate ? "/dah sell <цена>" : "/ah sell <цена>");
            return true;
        }
        if (donate) plugin.sellPoints(player, args[1]);
        else plugin.sell(player, args[1]);
        return true;
    }

    private boolean kit(Player player, String[] args) {
        if (args.length < 2) {
            usage(player, donate ? "/dah kit <цена>" : "/ah kit <цена>");
            return true;
        }
        plugin.sellKit(player, args[1], donate);
        return true;
    }

    private boolean search(Player player, String[] args) {
        if (args.length < 2) {
            usage(player, donate ? "/dah search <название>" : "/ah search <название>");
            return true;
        }
        String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (query.isEmpty()) {
            usage(player, donate ? "/dah search <название>" : "/ah search <название>");
            return true;
        }
        plugin.openAuctionSearch(player, query, donate);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
        return true;
    }

    private boolean show(Player player, String[] args) {
        if (args.length < 2) {
            usage(player, donate ? "/dah show <игрок>" : "/ah show <игрок>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            reject(player, plugin.messages().message("error.player-not-found"));
            return true;
        }
        plugin.openSellerGui(player, target.getUniqueId(), donate);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
        return true;
    }

    private boolean help(Player player) {
        String root = donate ? "/dah" : "/ah";
        player.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("  §6§lАУКЦИОН");
        player.sendMessage("§8  ▸ §e" + root + " §8— §fоткрыть аукцион");
        player.sendMessage("§8  ▸ §e" + root + " sell <цена> §8— §fвыставить предмет из руки");
        player.sendMessage("§8  ▸ §e" + root + " kit <цена> §8— §fвыставить набор из инвентаря");
        player.sendMessage("§8  ▸ §e" + root + " search <название> §8— §fнайти лот");
        player.sendMessage("§8  ▸ §e" + root + " show <игрок> §8— §fпосмотреть товары игрока");
        if (!donate && player.hasPermission("pnmarket.admin")) {
            player.sendMessage("§8  ▸ §e/ah reload §8— §fперезагрузить конфиг");
        }
        player.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return true;
    }

    private void usage(Player player, String command) {
        player.sendMessage(plugin.messages().message("command.usage", Map.of("command", command)));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
    }

    private void noPermission(Player player) {
        reject(player, plugin.messages().message("command.no-permission"));
    }

    private void reject(Player player, String message) {
        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
    }
}
