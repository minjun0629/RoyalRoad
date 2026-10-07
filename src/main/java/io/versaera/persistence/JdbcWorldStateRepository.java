package io.versaera.persistence;

import io.versaera.application.port.WorldStateRepository;

import java.util.List;

public final class JdbcWorldStateRepository implements WorldStateRepository {
    private final Jdbc j;

    public JdbcWorldStateRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public void remember(String uuid, String npcId, String kind, String detail, int weight, long at) {
        j.update("INSERT INTO npc_memory (uuid, npc_id, kind, detail, weight, at) VALUES (?, ?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, npcId);
            ps.setString(3, kind);
            ps.setString(4, detail);
            ps.setInt(5, weight);
            ps.setLong(6, at);
        });
    }

    @Override
    public List<Memory> memories(String uuid, String npcId, int limit) {
        return j.query("SELECT npc_id, kind, detail, weight, at FROM npc_memory WHERE uuid = ? AND npc_id = ? ORDER BY at DESC, id DESC LIMIT ?", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, npcId);
            ps.setInt(3, limit);
        }, rs -> new Memory(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getLong(5)));
    }

    @Override
    public int count(String uuid, String npcId, String kind) {
        return j.one("SELECT COUNT(*) FROM npc_memory WHERE uuid = ? AND npc_id = ? AND kind = ?", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, npcId);
            ps.setString(3, kind);
        }, rs -> rs.getInt(1), 0);
    }

    @Override
    public Prosperity prosperity(String region) {
        return j.one("SELECT prosperity, updated_at FROM region_state WHERE region = ?", ps -> ps.setString(1, region),
                rs -> new Prosperity(rs.getInt(1), rs.getLong(2)), new Prosperity(0, 0));
    }

    @Override
    public void setProsperity(String region, int value, long at) {
        j.update("INSERT INTO region_state (region, prosperity, updated_at) VALUES (?, ?, ?) "
                + "ON CONFLICT (region) DO UPDATE SET prosperity = excluded.prosperity, updated_at = excluded.updated_at", ps -> {
            ps.setString(1, region);
            ps.setInt(2, value);
            ps.setLong(3, at);
        });
    }

    @Override
    public boolean contribute(String key, String region, int amount, long at) {
        return j.update("INSERT OR IGNORE INTO region_contribution (request_key, region, amount, at) VALUES (?, ?, ?, ?)", ps -> {
            ps.setString(1, key);
            ps.setString(2, region);
            ps.setInt(3, amount);
            ps.setLong(4, at);
        }) > 0;
    }
}
