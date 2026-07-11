package ru.privatenull.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import ru.privatenull.model.MarketListing;
import ru.privatenull.model.PurchaseReservation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** JDBC storage shared by the embedded SQLite and external MySQL backends. */
public final class JdbcMarketRepository implements MarketStorage {
    private final Connection connection;
    private final long expiryMillis;
    private final Logger logger;

    public JdbcMarketRepository(String driver, String url, String username, String password,
                                long expiryMillis, Logger logger) {
        try {
            Class.forName(driver);
            connection = (username == null || username.isBlank())
                    ? DriverManager.getConnection(url)
                    : DriverManager.getConnection(url, username, password == null ? "" : password);
            this.expiryMillis = expiryMillis;
            this.logger = logger;
            createTable();
        } catch (ClassNotFoundException | SQLException exception) {
            throw new IllegalStateException("Не удалось открыть SQL-хранилище: " + exception.getMessage(), exception);
        }
    }

    @Override
    public synchronized MarketListing create(UUID sellerId, ItemStack item, double pricePerUnit,
                                              int amount, long createdAt) throws IOException {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO pnmarket_listings (id, seller, item, price_per_unit, amount, created_at, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, sellerId.toString());
            statement.setString(3, encodeItem(item));
            statement.setDouble(4, pricePerUnit);
            statement.setInt(5, amount);
            statement.setLong(6, createdAt);
            statement.executeUpdate();
            return new MarketListing(id, sellerId, item.clone(), pricePerUnit, amount, createdAt, "ACTIVE");
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось сохранить лот: " + exception.getMessage(), exception);
        }
    }

    @Override
    public synchronized List<MarketListing> findAll() {
        return find("SELECT * FROM pnmarket_listings", null);
    }

    @Override
    public synchronized List<MarketListing> findBySeller(UUID sellerId) {
        return find("SELECT * FROM pnmarket_listings WHERE seller = ?", sellerId.toString());
    }

    @Override
    public synchronized Optional<MarketListing> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        List<MarketListing> listings = find("SELECT * FROM pnmarket_listings WHERE id = ?", id);
        return listings.stream().findFirst();
    }

    @Override
    public synchronized boolean hasActiveListings(UUID sellerId) {
        return countActiveListings(sellerId) > 0;
    }

    @Override
    public synchronized int countActiveListings(UUID sellerId) {
        String sql = "SELECT COUNT(*) FROM pnmarket_listings WHERE seller = ? AND status = 'ACTIVE' AND amount > 0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sellerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Math.min(result.getInt(1), Integer.MAX_VALUE) : 0;
            }
        } catch (SQLException exception) {
            throw storageError(exception);
        }
    }

    @Override
    public synchronized void delete(String id) {
        executeUpdate("DELETE FROM pnmarket_listings WHERE id = ?", id);
    }

    @Override
    public synchronized void updateAmount(String id, int amount) {
        executeUpdate("UPDATE pnmarket_listings SET amount = ? WHERE id = ?", amount, id);
    }

    @Override
    public synchronized void updateStatus(String id, String status) {
        executeUpdate("UPDATE pnmarket_listings SET status = ? WHERE id = ?", status, id);
    }

    @Override
    public synchronized Optional<PurchaseReservation> reserve(String id, int requestedAmount) {
        if (id == null || id.isBlank() || requestedAmount <= 0) return Optional.empty();
        Optional<MarketListing> result = findById(id);
        if (result.isEmpty() || !"ACTIVE".equalsIgnoreCase(result.get().status()) || result.get().amount() <= 0) {
            return Optional.empty();
        }
        MarketListing listing = result.get();
        int quantity = Math.min(requestedAmount, listing.amount());
        updateAmount(id, listing.amount() - quantity);
        return Optional.of(new PurchaseReservation(listing, quantity, listing.amount() - quantity));
    }

    @Override
    public synchronized void rollbackReservation(String id, int quantity) {
        if (id == null || quantity <= 0) return;
        String sql = "UPDATE pnmarket_listings SET amount = amount + ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, quantity);
            statement.setString(2, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw storageError(exception);
        }
    }

    @Override
    public synchronized void finalizeReservation(PurchaseReservation reservation) {
        if (reservation.remainingAmount() == 0) delete(reservation.listing().id());
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Не удалось закрыть SQL-хранилище: " + exception.getMessage());
        }
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS pnmarket_listings ("
                + "id VARCHAR(36) PRIMARY KEY, seller VARCHAR(36) NOT NULL, item TEXT NOT NULL, "
                + "price_per_unit DOUBLE NOT NULL, amount INTEGER NOT NULL, created_at BIGINT NOT NULL, "
                + "status VARCHAR(16) NOT NULL)";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<MarketListing> find(String sql, String parameter) {
        List<MarketListing> listings = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) statement.setString(1, parameter);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) decode(result).ifPresent(listings::add);
            }
            return listings;
        } catch (SQLException exception) {
            throw storageError(exception);
        }
    }

    private Optional<MarketListing> decode(ResultSet result) {
        try {
            String id = result.getString("id");
            String status = result.getString("status");
            long createdAt = result.getLong("created_at");
            if ("ACTIVE".equalsIgnoreCase(status) && System.currentTimeMillis() - createdAt >= expiryMillis) {
                updateStatus(id, "EXPIRED");
                status = "EXPIRED";
            }
            return Optional.of(new MarketListing(id, UUID.fromString(result.getString("seller")),
                    decodeItem(result.getString("item")), result.getDouble("price_per_unit"),
                    result.getInt("amount"), createdAt, status));
        } catch (SQLException | IOException | IllegalArgumentException exception) {
            logger.log(Level.WARNING, "Пропущен повреждённый SQL-лот: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private void executeUpdate(String sql, Object... values) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                if (values[index] instanceof Integer value) statement.setInt(index + 1, value);
                else statement.setString(index + 1, String.valueOf(values[index]));
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw storageError(exception);
        }
    }

    private IllegalStateException storageError(SQLException exception) {
        return new IllegalStateException("Ошибка SQL-хранилища: " + exception.getMessage(), exception);
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
        try (ByteArrayInputStream input = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream data = new BukkitObjectInputStream(input)) {
            Object value = data.readObject();
            if (!(value instanceof ItemStack item)) throw new IOException("item data has invalid type");
            return item;
        } catch (ClassNotFoundException | IllegalArgumentException exception) {
            throw new IOException("invalid item data", exception);
        }
    }
}
