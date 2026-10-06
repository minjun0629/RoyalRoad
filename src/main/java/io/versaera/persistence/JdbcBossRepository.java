package io.versaera.persistence;

import io.versaera.application.port.BossRepository;
import io.versaera.domain.boss.BossRewards.Contribution;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JdbcBossRepository implements BossRepository {
    private final Jdbc j;

    public JdbcBossRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public void start(String fightId, String bossId, long at) {
        j.update("INSERT INTO boss_fight (id, boss_id, state, started_at) VALUES (?, ?, 'ACTIVE', ?)", ps -> {
            ps.setString(1, fightId);
            ps.setString(2, bossId);
            ps.setLong(3, at);
        });
    }

    @Override
    public boolean finish(String fightId, String state, long at) {
        return j.update("UPDATE boss_fight SET state = ?, ended_at = ? WHERE id = ? AND state = 'ACTIVE'", ps -> {
            ps.setString(1, state);
            ps.setLong(2, at);
            ps.setString(3, fightId);
        }) == 1;
    }

    @Override
    public void saveContribution(String fightId, String uuid, Contribution c) {
        j.update("INSERT INTO boss_contribution (fight_id, uuid, damage, mitigated, support) VALUES (?, ?, ?, ?, ?) "
                + "ON CONFLICT (fight_id, uuid) DO UPDATE SET damage = excluded.damage, mitigated = excluded.mitigated, support = excluded.support", ps -> {
            ps.setString(1, fightId);
            ps.setString(2, uuid);
            ps.setLong(3, c.damage());
            ps.setLong(4, c.mitigated());
            ps.setLong(5, c.support());
        });
    }

    @Override
    public Map<String, Contribution> contributions(String fightId) {
        Map<String, Contribution> m = new LinkedHashMap<>();
        j.query("SELECT uuid, damage, mitigated, support FROM boss_contribution WHERE fight_id = ?", ps -> ps.setString(1, fightId),
                rs -> m.put(rs.getString(1), new Contribution(rs.getLong(2), rs.getLong(3), rs.getLong(4))));
        return m;
    }

    @Override
    public int failAllActive(long at) {
        return j.update("UPDATE boss_fight SET state = 'FAILED', ended_at = ? WHERE state = 'ACTIVE'", ps -> ps.setLong(1, at));
    }
}
