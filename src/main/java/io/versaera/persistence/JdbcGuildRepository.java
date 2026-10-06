package io.versaera.persistence;

import io.versaera.application.port.GuildRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class JdbcGuildRepository implements GuildRepository {
    private static final String G = "SELECT id, name, tag, leader, level, xp, created_at FROM guild ";
    private static final String M = "SELECT uuid, guild_id, rank, contribution, joined_at FROM guild_member ";
    private final Jdbc j;

    public JdbcGuildRepository(Database db) {
        this.j = new Jdbc(db);
    }

    private static Guild g(ResultSet rs) throws SQLException {
        return new Guild(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getLong(6), rs.getLong(7));
    }

    private static Member m(ResultSet rs) throws SQLException {
        return new Member(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5));
    }

    @Override
    public void create(Guild g) {
        j.update("INSERT INTO guild (id, name, tag, leader, level, xp, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, g.id());
            ps.setString(2, g.name());
            ps.setString(3, g.tag());
            ps.setString(4, g.leader());
            ps.setInt(5, g.level());
            ps.setLong(6, g.xp());
            ps.setLong(7, g.createdAt());
        });
    }

    @Override
    public Optional<Guild> find(String id) {
        return Optional.ofNullable(j.one(G + "WHERE id = ?", ps -> ps.setString(1, id), JdbcGuildRepository::g, null));
    }

    @Override
    public Optional<Guild> byName(String name) {
        return Optional.ofNullable(j.one(G + "WHERE lower(name) = lower(?)", ps -> ps.setString(1, name), JdbcGuildRepository::g, null));
    }

    @Override
    public boolean nameOrTagTaken(String name, String tag) {
        return j.one("SELECT COUNT(*) FROM guild WHERE lower(name) = lower(?) OR upper(tag) = upper(?)", ps -> {
            ps.setString(1, name);
            ps.setString(2, tag);
        }, rs -> rs.getInt(1), 0) > 0;
    }

    @Override
    public void update(Guild g) {
        j.update("UPDATE guild SET leader = ?, level = ?, xp = ? WHERE id = ?", ps -> {
            ps.setString(1, g.leader());
            ps.setInt(2, g.level());
            ps.setLong(3, g.xp());
            ps.setString(4, g.id());
        });
    }

    @Override
    public void delete(String id) {
        j.update("DELETE FROM guild_member WHERE guild_id = ?", ps -> ps.setString(1, id));
        j.update("DELETE FROM guild WHERE id = ?", ps -> ps.setString(1, id));
    }

    @Override
    public Optional<Member> member(String uuid) {
        return Optional.ofNullable(j.one(M + "WHERE uuid = ?", ps -> ps.setString(1, uuid), JdbcGuildRepository::m, null));
    }

    @Override
    public List<Member> members(String guildId) {
        return j.query(M + "WHERE guild_id = ? ORDER BY joined_at", ps -> ps.setString(1, guildId), JdbcGuildRepository::m);
    }

    @Override
    public void addMember(Member m) {
        j.update("INSERT INTO guild_member (uuid, guild_id, rank, contribution, joined_at) VALUES (?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, m.uuid());
            ps.setString(2, m.guildId());
            ps.setString(3, m.rank());
            ps.setLong(4, m.contribution());
            ps.setLong(5, m.joinedAt());
        });
    }

    @Override
    public void setRank(String uuid, String rank) {
        j.update("UPDATE guild_member SET rank = ? WHERE uuid = ?", ps -> {
            ps.setString(1, rank);
            ps.setString(2, uuid);
        });
    }

    @Override
    public void addContribution(String uuid, long delta) {
        j.update("UPDATE guild_member SET contribution = contribution + ? WHERE uuid = ?", ps -> {
            ps.setLong(1, delta);
            ps.setString(2, uuid);
        });
    }

    @Override
    public void removeMember(String uuid) {
        j.update("DELETE FROM guild_member WHERE uuid = ?", ps -> ps.setString(1, uuid));
    }
}
