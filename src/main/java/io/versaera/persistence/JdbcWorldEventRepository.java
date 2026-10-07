package io.versaera.persistence;

import io.versaera.application.port.WorldEventRepository;

import java.util.HashMap;
import java.util.Map;

public final class JdbcWorldEventRepository implements WorldEventRepository {
    private final Jdbc j;

    public JdbcWorldEventRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public Map<String, Boolean> states() {
        Map<String, Boolean> m = new HashMap<>();
        j.query("SELECT event_id, active FROM world_event_state", ps -> {
        }, rs -> m.put(rs.getString(1), rs.getInt(2) == 1));
        return m;
    }

    @Override
    public void set(String eventId, boolean active, long startedAt, long nextAt) {
        j.update("INSERT INTO world_event_state (event_id, active, started_at, next_at) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (event_id) DO UPDATE SET active = excluded.active, started_at = excluded.started_at, next_at = excluded.next_at", ps -> {
            ps.setString(1, eventId);
            ps.setInt(2, active ? 1 : 0);
            ps.setLong(3, startedAt);
            ps.setLong(4, nextAt);
        });
    }
}
