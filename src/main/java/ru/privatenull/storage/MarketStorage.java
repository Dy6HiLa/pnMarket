package ru.privatenull.storage;

import org.bukkit.inventory.ItemStack;
import ru.privatenull.model.MarketListing;
import ru.privatenull.model.PurchaseReservation;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketStorage extends AutoCloseable {
    default MarketListing create(UUID sellerId, ItemStack item, double pricePerUnit,
                                 int amount, long createdAt) throws IOException {
        return create(sellerId, item, pricePerUnit, amount, createdAt, Long.MAX_VALUE);
    }

    MarketListing create(UUID sellerId, ItemStack item, double pricePerUnit, int amount,
                         long createdAt, long expiresAt) throws IOException;

    List<MarketListing> findAll();

    List<MarketListing> findBySeller(UUID sellerId);

    Optional<MarketListing> findById(String id);

    boolean hasActiveListings(UUID sellerId);

    int countActiveListings(UUID sellerId);

    void delete(String id);

    void updateAmount(String id, int amount);

    void updateStatus(String id, String status);

    void relist(String id, long createdAt, long expiresAt);

    Optional<PurchaseReservation> reserve(String id, int requestedAmount);

    void rollbackReservation(String id, int quantity);

    void finalizeReservation(PurchaseReservation reservation);

    @Override
    void close();
}
