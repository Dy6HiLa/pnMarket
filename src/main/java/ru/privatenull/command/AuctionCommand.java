package ru.privatenull.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.privatenull.PnMarketPlugin;
import ru.privatenull.market.MarketSearch;

import java.util.Locale;
import java.util.Map;

public final class AuctionCommand implements CommandExecutor {
    private final PnMarketPlugin plugin;
    private final boolean donate;

    public AuctionCommand(PnMarketPlugin plugin, boolean donate) {
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
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, .20f, 1.1f);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> sell(player, args);
            case "kit" -> kit(player, args);
            case "notify" -> notify(player, args);
            case "search" -> search(player, args);
            case "show", "snow" -> show(player, args);
            case "help", "?" -> help(player);
            default -> help(player);
        };
    }

    private boolean sell(Player player, String[] args) {
        if (args.length < 2) {
            usage(player, donate ? "/dah sell <цена>" : "/ah sell <цена>");
            return true;
        }
        if (args[1].equalsIgnoreCase("auto")) {
            plugin.sellAuto(player, donate);
            return true;
        }
        if (donate) plugin.sellPoints(player, args[1]);
        else plugin.sell(player, args[1]);
        return true;
    }

    private boolean kit(Player player, String[] args) {
        if (args.length < 2) {
            usage(player, donate ? "/dah kit <цена> [название]" : "/ah kit <цена> [название]");
            return true;
        }
        String name = args.length > 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                : "Набор";
        plugin.sellKit(player, args[1], donate, name);
        return true;
    }

    private boolean notify(Player player, String[] args) {
        String root = donate ? "/dah" : "/ah";
        if (args.length == 1) {
            plugin.openNotificationCatalog(player, donate);
            return true;
        }
        usage(player, root + " notify");
        return true;
    }

    private boolean search(Player player, String[] args) {
        if (args.length < 2) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                usage(player, donate ? "/dah search <название>" : "/ah search <название>");
                return true;
            }
            String query = MarketSearch.extractName(hand);
            plugin.openAuctionSearch(player, query, donate);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .20f, 1.4f);
            return true;
        }
        String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (query.isEmpty()) {
            usage(player, donate ? "/dah search <название>" : "/ah search <название>");
            return true;
        }
        plugin.openAuctionSearch(player, query, donate);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .20f, 1.4f);
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
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, .20f, 1.1f);
        return true;
    }

    private boolean help(Player player) {
        String root = donate ? "/dah" : "/ah";
        player.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("  §6§lАУКЦИОН");
        player.sendMessage("§8  ▸ §e" + root + " §8— §fоткрыть аукцион");
        player.sendMessage("§8  ▸ §e" + root + " sell <цена> §8— §fвыставить предмет из руки");
        player.sendMessage("§8  ▸ §e" + root + " sell auto §8— §fавтоматически рассчитать цену");
        player.sendMessage("§8  ▸ §e" + root + " kit <цена> [название] §8— §fвыставить набор");
        player.sendMessage("§8  ▸ §e" + root + " notify §8— §fуведомления о снижении цены");
        player.sendMessage("§8  ▸ §e" + root + " search <название> §8— §fнайти лот");
        player.sendMessage("§8  ▸ §e" + root + " show <игрок> §8— §fпосмотреть товары игрока");
        player.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return true;
    }

    private void usage(Player player, String command) {
        player.sendMessage(plugin.messages().message("command.usage", Map.of("command", command)));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, .20f, 0.8f);
    }

    private void reject(Player player, String message) {
        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, .20f, 0.8f);
    }
}
