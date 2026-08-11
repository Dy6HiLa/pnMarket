package ru.privatenull.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class MarketListing {
    private final String id;
    private final UUID sellerId;
    private final ItemStack item;
    private final String currencyId;
    private final double pricePerUnit;
    private final int amount;
    private final long createdAt;
    private final String status;

    public MarketListing(String id, UUID sellerId, ItemStack item, String currencyId, double pricePerUnit,
                         int amount, long createdAt, String status) {
        if (currencyId == null || currencyId.isBlank()) throw new IllegalArgumentException("currencyId is required");
        this.id = id;
        this.sellerId = sellerId;
        this.item = item;
        this.currencyId = currencyId.toLowerCase(java.util.Locale.ROOT);
        this.pricePerUnit = pricePerUnit;
        this.amount = amount;
        this.createdAt = createdAt;
        this.status = status;
    }

    public MarketListing(String id, UUID sellerId, ItemStack item, double pricePerUnit,
                         int amount, long createdAt, String status) {
        this(id, sellerId, item, "vault", pricePerUnit, amount, createdAt, status);
    }

    public String id() { return id; }
    public UUID sellerId() { return sellerId; }
    public ItemStack item() { return item; }
    public String currencyId() { return currencyId; }
    public double pricePerUnit() { return pricePerUnit; }
    public int amount() { return amount; }
    public long createdAt() { return createdAt; }
    public String status() { return status; }

    public MarketListing withAmount(int newAmount) {
        return new MarketListing(id, sellerId, item, currencyId, pricePerUnit, newAmount, createdAt, status);
    }

    public MarketListing withStatus(String newStatus) {
        return new MarketListing(id, sellerId, item, currencyId, pricePerUnit, amount, createdAt, newStatus);
    }
}
