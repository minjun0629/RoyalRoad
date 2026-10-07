package io.versaera.persistence;

import io.versaera.application.port.MarketRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

public final class JdbcMarketRepository implements MarketRepository {
    private static final String L = "SELECT id, seller, market, kind, item_id, type_id, quality, amount, price, state, buyer, created_at, expires_at, "
            + "closed_at FROM auction_listing ";
    private final Jdbc j;

    public JdbcMarketRepository(Database db) {
        this.j = new Jdbc(db);
    }

    private static Listing l(ResultSet rs) throws SQLException {
        long c = rs.getLong(14);
        Long closed = rs.wasNull() ? null : c;
        return new Listing(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7),
                rs.getInt(8), rs.getLong(9), rs.getString(10), rs.getString(11), rs.getLong(12), rs.getLong(13), closed);
    }

    @Override
    public Supply supply(String market, String typeId) {
        return j.one("SELECT supply, updated_at FROM market_supply WHERE market = ? AND type_id = ?", ps -> {
            ps.setString(1, market);
            ps.setString(2, typeId);
        }, rs -> new Supply(rs.getLong(1), rs.getLong(2)), new Supply(0, 0));
    }

    @Override
    public void setSupply(String market, String typeId, long supply, long at) {
        j.update("INSERT INTO market_supply (market, type_id, supply, updated_at) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (market, type_id) DO UPDATE SET supply = excluded.supply, updated_at = excluded.updated_at", ps -> {
            ps.setString(1, market);
            ps.setString(2, typeId);
            ps.setLong(3, supply);
            ps.setLong(4, at);
        });
    }

    @Override
    public void insert(Listing l) {
        j.update("INSERT INTO auction_listing (id, seller, market, kind, item_id, type_id, quality, amount, price, state, buyer, created_at, expires_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, l.id());
            ps.setString(2, l.seller());
            ps.setString(3, l.market());
            ps.setString(4, l.kind());
            if (l.itemId() == null) ps.setNull(5, Types.VARCHAR);
            else ps.setString(5, l.itemId());
            ps.setString(6, l.typeId());
            ps.setInt(7, l.quality());
            ps.setInt(8, l.amount());
            ps.setLong(9, l.price());
            ps.setString(10, l.state());
            ps.setString(11, l.buyer());
            ps.setLong(12, l.createdAt());
            ps.setLong(13, l.expiresAt());
        });
    }

    @Override
    public Optional<Listing> find(String id) {
        return Optional.ofNullable(j.one(L + "WHERE id = ?", ps -> ps.setString(1, id), JdbcMarketRepository::l, null));
    }

    @Override
    public boolean close(String id, String expected, String state, String buyer, long at) {
        return j.update("UPDATE auction_listing SET state = ?, buyer = ?, closed_at = ? WHERE id = ? AND state = ?", ps -> {
            ps.setString(1, state);
            ps.setString(2, buyer);
            ps.setLong(3, at);
            ps.setString(4, id);
            ps.setString(5, expected);
        }) == 1;
    }

    @Override
    public List<Listing> open(String market, String typeId, int limit) {
        if (typeId == null) return j.query(L + "WHERE state = 'OPEN' AND market = ? ORDER BY price / amount, created_at LIMIT ?", ps -> {
            ps.setString(1, market);
            ps.setInt(2, limit);
        }, JdbcMarketRepository::l);
        return j.query(L + "WHERE state = 'OPEN' AND market = ? AND type_id = ? ORDER BY price / amount, created_at LIMIT ?", ps -> {
            ps.setString(1, market);
            ps.setString(2, typeId);
            ps.setInt(3, limit);
        }, JdbcMarketRepository::l);
    }

    @Override
    public List<Listing> bySeller(String seller, String state) {
        return j.query(L + "WHERE seller = ? AND state = ? ORDER BY created_at", ps -> {
            ps.setString(1, seller);
            ps.setString(2, state);
        }, JdbcMarketRepository::l);
    }

    @Override
    public List<Listing> expired(long now, int limit) {
        return j.query(L + "WHERE state = 'OPEN' AND expires_at <= ? LIMIT ?", ps -> {
            ps.setLong(1, now);
            ps.setInt(2, limit);
        }, JdbcMarketRepository::l);
    }
}
