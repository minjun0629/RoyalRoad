package io.versaera.persistence;

import io.versaera.application.port.DungeonRepository;

public final class JdbcDungeonRepository implements DungeonRepository {
    private final Jdbc j;

    public JdbcDungeonRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public void insert(String id, String dungeonId, long seed, String members, long at) {
        j.update("INSERT INTO dungeon_run (id, dungeon_id, seed, state, members, started_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)", ps -> {
            ps.setString(1, id);
            ps.setString(2, dungeonId);
            ps.setLong(3, seed);
            ps.setString(4, members);
            ps.setLong(5, at);
        });
    }

    @Override
    public void setState(String id, String state, long at) {
        j.update("UPDATE dungeon_run SET state = ?, ended_at = ? WHERE id = ? AND state = 'ACTIVE'", ps -> {
            ps.setString(1, state);
            ps.setLong(2, at);
            ps.setString(3, id);
        });
    }

    @Override
    public int failAllActive(long at) {
        return j.update("UPDATE dungeon_run SET state = 'FAILED', ended_at = ? WHERE state = 'ACTIVE'", ps -> ps.setLong(1, at));
    }
}
