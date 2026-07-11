package ru.privatenull.market;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.localization.ItemLocalization;
import ru.privatenull.model.MarketListing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MarketSearch {

    private MarketSearch() {
    }

    public static String extractName(MarketListing listing) {
        if (listing == null || listing.item() == null) return null;
        return extractName(listing.item());
    }

    public static String extractName(ItemStack item) {
        if (item == null) return null;

        String fromLocalization = ItemLocalization.getPlainName(item);
        if (fromLocalization != null && !fromLocalization.isEmpty()) {
            String stripped = ChatColor.stripColor(fromLocalization).trim();
            if (!stripped.isEmpty()) {
                return stripped;
            }
        }

        String mat = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (mat.isEmpty()) return null;
        return Character.toUpperCase(mat.charAt(0)) + mat.substring(1);
    }

    public static boolean matches(MarketListing listing, String query) {
        if (query == null || query.isEmpty()) return true;
        if (listing == null || listing.item() == null) return false;

        String q = query.toLowerCase(Locale.ROOT);

        String name = extractName(listing);
        if (name != null && name.toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }

        String mat = listing.item().getType().name().toLowerCase(Locale.ROOT);
        return mat.contains(q);
    }

    public static List<String> tabComplete(Collection<MarketListing> listings, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        Set<String> result = new LinkedHashSet<>();
        for (MarketListing listing : listings) {
            if (listing == null || listing.item() == null) continue;
            String name = extractName(listing);
            if (name == null) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith(p)) {
                result.add(name);
                if (result.size() >= 20) break;
            }
        }
        return new ArrayList<>(result);
    }
}
