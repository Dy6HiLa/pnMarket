package ru.privatenull.storage;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import ru.privatenull.model.MarketListing;
import ru.privatenull.model.PurchaseReservation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MarketRepository implements MarketStorage {
    private final MongoClient client;
    private final MongoCollection<Document> collection;
    private final long expiryMillis;
    private final Logger logger;

    public MarketRepository(String uri, String databaseName, String collectionName,
                            long expiryMillis, Logger logger) {
        this.client = MongoClients.create(uri);
        MongoDatabase database = client.getDatabase(databaseName);
        database.runCommand(new Document("ping", 1));
        this.collection = database.getCollection(collectionName);
        this.expiryMillis = expiryMillis;
        this.logger = logger;
    }

    public MarketListing create(UUID sellerId, ItemStack item, double pricePerUnit,
                                int amount, long createdAt) throws IOException {
        String encodedItem = encodeItem(item);
        while (true) {
            String id = UUID.randomUUID().toString();
            Document document = new Document("_id", id)
                    .append("seller", sellerId.toString())
                    .append("item", encodedItem)
                    .append("pricePerUnit", pricePerUnit)
                    .append("amount", amount)
                    .append("createdAt", createdAt)
                    .append("status", "ACTIVE");
            try {
                collection.insertOne(document);
                return new MarketListing(id, sellerId, item.clone(), pricePerUnit, amount, createdAt, "ACTIVE");
            } catch (MongoWriteException exception) {
                if (exception.getError() != null && exception.getError().getCode() == 11000) continue;
                throw exception;
            }
        }
    }

    public List<MarketListing> findAll() {
        List<MarketListing> listings = new ArrayList<>();
        for (Document document : collection.find()) decode(document).ifPresent(listings::add);
        return listings;
    }

    public List<MarketListing> findBySeller(UUID sellerId) {
        List<MarketListing> listings = new ArrayList<>();
        for (Document document : collection.find(Filters.eq("seller", sellerId.toString()))) {
            decode(document).ifPresent(listings::add);
        }
        return listings;
    }

    public Optional<MarketListing> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        Document document = collection.find(Filters.eq("_id", id)).first();
        return document == null ? Optional.empty() : decode(document);
    }

    public boolean hasActiveListings(UUID sellerId) {
        return collection.countDocuments(Filters.and(
                Filters.eq("seller", sellerId.toString()),
                Filters.eq("status", "ACTIVE"),
                Filters.gt("amount", 0)
        )) > 0;
    }

    public int countActiveListings(UUID sellerId) {
        long count = collection.countDocuments(Filters.and(
                Filters.eq("seller", sellerId.toString()),
                Filters.eq("status", "ACTIVE"),
                Filters.gt("amount", 0)
        ));
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    public void delete(String id) {
        collection.deleteOne(Filters.eq("_id", id));
    }

    public void updateAmount(String id, int amount) {
        collection.updateOne(Filters.eq("_id", id),
                new Document("$set", new Document("amount", amount)));
    }

    public void updateStatus(String id, String status) {
        collection.updateOne(Filters.eq("_id", id),
                new Document("$set", new Document("status", status)));
    }

    public Optional<PurchaseReservation> reserve(String id, int requestedAmount) {
        if (id == null || id.isBlank() || requestedAmount <= 0) return Optional.empty();
        for (int attempt = 0; attempt < 5; attempt++) {
            Document current = collection.find(Filters.and(
                    Filters.eq("_id", id),
                    Filters.eq("status", "ACTIVE"),
                    Filters.gt("amount", 0)
            )).first();
            if (current == null) return Optional.empty();
            int available = current.getInteger("amount", 0);
            int quantity = Math.min(requestedAmount, available);
            if (quantity <= 0) return Optional.empty();

            Document reserved = collection.findOneAndUpdate(
                    Filters.and(
                            Filters.eq("_id", id),
                            Filters.eq("status", "ACTIVE"),
                            Filters.gte("amount", quantity)
                    ),
                    Updates.inc("amount", -quantity),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE)
            );
            if (reserved == null) continue;
            Optional<MarketListing> listing = decode(reserved);
            if (listing.isEmpty()) {
                rollbackReservation(id, quantity);
                return Optional.empty();
            }
            return Optional.of(new PurchaseReservation(listing.get(), quantity, available - quantity));
        }
        return Optional.empty();
    }

    public void rollbackReservation(String id, int quantity) {
        if (id == null || quantity <= 0) return;
        collection.updateOne(Filters.eq("_id", id), Updates.inc("amount", quantity));
    }

    public void finalizeReservation(PurchaseReservation reservation) {
        if (reservation.remainingAmount() == 0) {
            collection.deleteOne(Filters.and(
                    Filters.eq("_id", reservation.listing().id()),
                    Filters.eq("amount", 0)
            ));
        }
    }

    @Override
    public void close() {
        client.close();
    }

    private Optional<MarketListing> decode(Document document) {
        String id = document.getString("_id");
        try {
            UUID sellerId = UUID.fromString(document.getString("seller"));
            Number rawPrice = document.get("pricePerUnit", Number.class);
            Number rawCreatedAt = document.get("createdAt", Number.class);
            if (rawPrice == null || rawCreatedAt == null) throw new IllegalArgumentException("missing numeric field");
            int amount = document.getInteger("amount", 0);
            ItemStack item = decodeItem(document.getString("item"));
            String status = document.getString("status");
            if (status == null) status = "ACTIVE";
            long createdAt = rawCreatedAt.longValue();
            if ("ACTIVE".equalsIgnoreCase(status)
                    && System.currentTimeMillis() - createdAt >= expiryMillis) {
                updateStatus(id, "EXPIRED");
                status = "EXPIRED";
            }
            return Optional.of(new MarketListing(
                    id, sellerId, item, rawPrice.doubleValue(), amount, createdAt, status
            ));
        } catch (IOException | IllegalArgumentException | ClassCastException exception) {
            logger.log(Level.WARNING, "Пропущен повреждённый лот MongoDB id=" + safeId(id)
                    + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    private String encodeItem(ItemStack item) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             BukkitObjectOutputStream data = new BukkitObjectOutputStream(output)) {
            data.writeObject(item);
            data.flush();
            return Base64.getEncoder().encodeToString(output.toByteArray());
        }
    }

    private ItemStack decodeItem(String encoded) throws IOException {
        if (encoded == null || encoded.isBlank()) throw new IOException("missing item data");
        try (ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream data = new BukkitObjectInputStream(input)) {
            Object value = data.readObject();
            if (!(value instanceof ItemStack item)) throw new IOException("item data has invalid type");
            return item;
        } catch (ClassNotFoundException | IllegalArgumentException exception) {
            throw new IOException("invalid item data", exception);
        }
    }

    private String safeId(String id) {
        return id == null || id.isBlank() ? "unknown" : id.replaceAll("[^A-Za-z0-9_-]", "?");
    }
}
