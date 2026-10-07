package io.versaera.persistence;

import io.versaera.application.port.OriginRepository;

import java.util.Optional;

public final class JdbcOriginRepository implements OriginRepository {
    private final Jdbc j;

    public JdbcOriginRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public Optional<Origin> find(String uuid) {
        return Optional.ofNullable(j.one("SELECT uuid, race, gender, city, created_at FROM character_origin WHERE uuid = ?", ps -> ps.setString(1, uuid),
                rs -> new Origin(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5)), null));
    }

    @Override
    public boolean create(Origin o) {
        return j.update("INSERT OR IGNORE INTO character_origin (uuid, race, gender, city, created_at) VALUES (?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, o.uuid());
            ps.setString(2, o.race());
            ps.setString(3, o.gender());
            ps.setString(4, o.city());
            ps.setLong(5, o.createdAt());
        }) == 1;
    }

    @Override
    public Optional<Lock> lock(String uuid) {
        return Optional.ofNullable(j.one("SELECT until, reason FROM login_lock WHERE uuid = ?", ps -> ps.setString(1, uuid),
                rs -> new Lock(rs.getLong(1), rs.getString(2)), null));
    }

    @Override
    public void setLock(String uuid, long until, String reason) {
        j.update("INSERT INTO login_lock (uuid, until, reason) VALUES (?, ?, ?) ON CONFLICT (uuid) DO UPDATE SET until = excluded.until, reason = excluded.reason", ps -> {
            ps.setString(1, uuid);
            ps.setLong(2, until);
            ps.setString(3, reason);
        });
    }

    @Override
    public void clearLock(String uuid) {
        j.update("DELETE FROM login_lock WHERE uuid = ?", ps -> ps.setString(1, uuid));
    }
}
