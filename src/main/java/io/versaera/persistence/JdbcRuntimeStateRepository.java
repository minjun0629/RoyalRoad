package io.versaera.persistence;

import io.versaera.application.port.RuntimeStateRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class JdbcRuntimeStateRepository implements RuntimeStateRepository {
    private final Jdbc j;

    public JdbcRuntimeStateRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public void put(String scope, String key, String data, long expiresAt, long now) {
        j.update("INSERT INTO runtime_state (scope, key, data, expires_at, updated_at) VALUES (?, ?, ?, ?, ?) "
                + "ON CONFLICT(scope, key) DO UPDATE SET data = excluded.data, expires_at = excluded.expires_at, updated_at = excluded.updated_at", ps -> {
            ps.setString(1, scope);
            ps.setString(2, key);
            ps.setString(3, data);
            ps.setLong(4, expiresAt);
            ps.setLong(5, now);
        });
    }

    @Override
    public Optional<Row> get(String scope, String key) {
        Row[] out = {null};
        j.query("SELECT scope, key, data, expires_at, updated_at FROM runtime_state WHERE scope = ? AND key = ?", ps -> {
            ps.setString(1, scope);
            ps.setString(2, key);
        }, rs -> out[0] = new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5)));
        return Optional.ofNullable(out[0]);
    }

    @Override
    public Map<String, Row> all(String scope) {
        Map<String, Row> out = new LinkedHashMap<>();
        j.query("SELECT scope, key, data, expires_at, updated_at FROM runtime_state WHERE scope = ? ORDER BY key", ps -> ps.setString(1, scope),
                rs -> out.put(rs.getString(2), new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5))));
        return out;
    }

    @Override
    public void delete(String scope, String key) {
        j.update("DELETE FROM runtime_state WHERE scope = ? AND key = ?", ps -> {
            ps.setString(1, scope);
            ps.setString(2, key);
        });
    }

    @Override
    public int purge(long now) {
        return j.update("DELETE FROM runtime_state WHERE expires_at > 0 AND expires_at <= ?", ps -> ps.setLong(1, now));
    }
}
