package io.versaera.persistence;

import io.versaera.application.port.QuestRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JdbcQuestRepository implements QuestRepository {
    private final Jdbc j;

    public JdbcQuestRepository(Database db) {
        this.j = new Jdbc(db);
    }

    private static Row map(ResultSet rs) throws SQLException {
        long c = rs.getLong("completed_at");
        return new Row(rs.getString("quest_id"), rs.getString("state"), rs.getString("progress"), rs.getString("choice"), rs.getInt("times"),
                rs.getLong("accepted_at"), rs.wasNull() ? null : c);
    }

    @Override
    public Optional<Row> find(String uuid, String questId) {
        return Optional.ofNullable(j.one("SELECT * FROM quest_progress WHERE uuid = ? AND quest_id = ?", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, questId);
        }, JdbcQuestRepository::map, null));
    }

    @Override
    public List<Row> all(String uuid) {
        return j.query("SELECT * FROM quest_progress WHERE uuid = ? ORDER BY accepted_at", ps -> ps.setString(1, uuid), JdbcQuestRepository::map);
    }

    @Override
    public void save(String uuid, Row r) {
        j.update("""
                INSERT INTO quest_progress (uuid, quest_id, state, progress, choice, times, accepted_at, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (uuid, quest_id) DO UPDATE SET state = excluded.state, progress = excluded.progress, choice = excluded.choice,
                    times = excluded.times, accepted_at = excluded.accepted_at, completed_at = excluded.completed_at""", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, r.questId());
            ps.setString(3, r.state());
            ps.setString(4, r.progress());
            ps.setString(5, r.choice());
            ps.setInt(6, r.times());
            ps.setLong(7, r.acceptedAt());
            if (r.completedAt() == null) ps.setNull(8, java.sql.Types.INTEGER);
            else ps.setLong(8, r.completedAt());
        });
    }

    @Override
    public int reputation(String uuid, String faction) {
        return j.one("SELECT value FROM reputation WHERE uuid = ? AND faction = ?", ps -> { ps.setString(1, uuid); ps.setString(2, faction); },
                rs -> rs.getInt(1), 0);
    }

    @Override
    public void addReputation(String uuid, String faction, int delta) {
        j.update("INSERT INTO reputation (uuid, faction, value) VALUES (?, ?, ?) ON CONFLICT (uuid, faction) DO UPDATE SET value = "
                + "MAX(-10000, MIN(10000, value + excluded.value))", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, faction);
            ps.setInt(3, Math.max(-10000, Math.min(10000, delta)));
        });
    }

    @Override
    public Map<String, Integer> reputations(String uuid) {
        Map<String, Integer> m = new LinkedHashMap<>();
        j.query("SELECT faction, value FROM reputation WHERE uuid = ? ORDER BY faction", ps -> ps.setString(1, uuid), rs -> m.put(rs.getString(1), rs.getInt(2)));
        return m;
    }
}
