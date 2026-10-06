package io.versaera.persistence;

import io.versaera.application.port.MapRepository;
import io.versaera.domain.map.FogMap;

import java.util.HashSet;
import java.util.Set;

public final class JdbcMapRepository implements MapRepository {
    private final Jdbc j;

    public JdbcMapRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public Set<Long> explored(String uuid) {
        Set<Long> out = new HashSet<>();
        j.query("SELECT cx, cz FROM map_explored WHERE uuid = ?", ps -> ps.setString(1, uuid), rs -> out.add(FogMap.pack(rs.getInt(1), rs.getInt(2))));
        return out;
    }

    @Override
    public int explore(String uuid, long[] cells) {
        int n = 0;
        for (long c : cells)
            n += j.update("INSERT OR IGNORE INTO map_explored (uuid, cx, cz) VALUES (?, ?, ?)", ps -> {
                ps.setString(1, uuid);
                ps.setInt(2, FogMap.unpackX(c));
                ps.setInt(3, FogMap.unpackZ(c));
            });
        return n;
    }
}
