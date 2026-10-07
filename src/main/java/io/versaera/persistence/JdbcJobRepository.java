package io.versaera.persistence;

import io.versaera.application.port.JobRepository;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JdbcJobRepository implements JobRepository {
    private final Jdbc j;

    public JdbcJobRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public Map<String, Held> jobs(String uuid) {
        Map<String, Held> m = new LinkedHashMap<>();
        j.query("SELECT slot, job_id, since FROM player_job WHERE uuid = ?", ps -> ps.setString(1, uuid),
                rs -> m.put(rs.getString(1), new Held(rs.getString(2), rs.getLong(3))));
        return m;
    }

    @Override
    public void set(String uuid, String slot, String jobId, long since) {
        j.update("INSERT INTO player_job (uuid, slot, job_id, since) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (uuid, slot) DO UPDATE SET job_id = excluded.job_id, since = excluded.since", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, slot);
            ps.setString(3, jobId);
            ps.setLong(4, since);
        });
    }

    @Override
    public void deathLog(String uuid, String region, int danger, long xpLost, long at) {
        j.update("INSERT INTO death_log (uuid, region, danger, xp_lost, created_at) VALUES (?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, region);
            ps.setInt(3, danger);
            ps.setLong(4, xpLost);
            ps.setLong(5, at);
        });
    }

    @Override
    public int deaths(String uuid) {
        return j.one("SELECT COUNT(*) FROM death_log WHERE uuid = ?", ps -> ps.setString(1, uuid), rs -> rs.getInt(1), 0);
    }
}
