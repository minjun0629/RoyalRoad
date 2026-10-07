package io.versaera.persistence;

import io.versaera.application.port.AdventureRepository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class JdbcAdventureRepository implements AdventureRepository {
    private final Jdbc j;

    public JdbcAdventureRepository(Database db) {
        this.j = new Jdbc(db);
    }

    private static Jdbc.Binder args(Object... a) {
        return ps -> bind(ps, a);
    }

    private static void bind(PreparedStatement ps, Object... a) throws SQLException {
        for (int i = 0; i < a.length; i++) {
            Object o = a[i];
            if (o instanceof Integer n) ps.setInt(i + 1, n);
            else if (o instanceof Long n) ps.setLong(i + 1, n);
            else ps.setString(i + 1, (String) o);
        }
    }

    // ------------------------------------------------------------------ 칭호
    @Override
    public Optional<String> title(String uuid) {
        return Optional.ofNullable(j.one("SELECT title FROM player_title WHERE uuid = ?", args(uuid), rs -> rs.getString(1), null));
    }

    @Override
    public void setTitle(String uuid, String title, long at) {
        j.update("INSERT INTO player_title (uuid, title, set_at) VALUES (?, ?, ?) ON CONFLICT (uuid) DO UPDATE SET title = excluded.title, set_at = excluded.set_at",
                args(uuid, title, at));
    }

    @Override
    public void clearTitle(String uuid) {
        j.update("DELETE FROM player_title WHERE uuid = ?", args(uuid));
    }

    // ------------------------------------------------------------------ 펫
    private static final String PET = "SELECT id, owner, species, name, level, xp, loyalty, fed_at, fainted_until, created_at FROM pet ";

    private static Pet pet(java.sql.ResultSet rs) throws SQLException {
        return new Pet(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getLong(6), rs.getInt(7),
                rs.getLong(8), rs.getLong(9), rs.getLong(10));
    }

    @Override
    public List<Pet> pets(String owner) {
        return j.query(PET + "WHERE owner = ? AND released = 0 ORDER BY created_at", args(owner), JdbcAdventureRepository::pet);
    }

    @Override
    public Optional<Pet> pet(String id) {
        return Optional.ofNullable(j.one(PET + "WHERE id = ? AND released = 0", args(id), JdbcAdventureRepository::pet, null));
    }

    @Override
    public void insertPet(Pet p) {
        j.update("INSERT INTO pet (id, owner, species, name, level, xp, loyalty, fed_at, fainted_until, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                args(p.id(), p.owner(), p.species(), p.name(), p.level(), p.xp(), p.loyalty(), p.fedAt(), p.faintedUntil(), p.createdAt()));
    }

    @Override
    public void updatePet(Pet p) {
        j.update("UPDATE pet SET name = ?, level = ?, xp = ?, loyalty = ?, fed_at = ?, fainted_until = ? WHERE id = ?",
                args(p.name(), p.level(), p.xp(), p.loyalty(), p.fedAt(), p.faintedUntil(), p.id()));
    }

    @Override
    public void releasePet(String id) {
        j.update("UPDATE pet SET released = 1 WHERE id = ?", args(id));
    }

    // ------------------------------------------------------------------ 탈것
    @Override
    public List<Mount> mounts(String owner) {
        return j.query("SELECT id, owner, kind, name, created_at FROM mount WHERE owner = ? ORDER BY created_at", args(owner),
                rs -> new Mount(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5)));
    }

    @Override
    public void insertMount(Mount m) {
        j.update("INSERT INTO mount (id, owner, kind, name, created_at) VALUES (?, ?, ?, ?, ?)", args(m.id(), m.owner(), m.kind(), m.name(), m.createdAt()));
    }

    // ------------------------------------------------------------------ 여행
    @Override
    public Optional<Journey> journey(String uuid) {
        return Optional.ofNullable(j.one("SELECT uuid, route, dest, depart_at, arrive_at FROM travel_journey WHERE uuid = ?", args(uuid),
                rs -> new Journey(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5)), null));
    }

    @Override
    public boolean startJourney(Journey x) {
        return j.update("INSERT OR IGNORE INTO travel_journey (uuid, route, dest, depart_at, arrive_at) VALUES (?, ?, ?, ?, ?)",
                args(x.uuid(), x.route(), x.dest(), x.departAt(), x.arriveAt())) > 0;
    }

    @Override
    public void endJourney(String uuid) {
        j.update("DELETE FROM travel_journey WHERE uuid = ?", args(uuid));
    }

    // ------------------------------------------------------------------ 길드 창고
    @Override
    public List<Stored> storage(String guildId) {
        return j.query("SELECT type_id, quality, amount FROM guild_storage WHERE guild_id = ? AND amount > 0 ORDER BY type_id, quality DESC", args(guildId),
                rs -> new Stored(rs.getString(1), rs.getInt(2), rs.getLong(3)));
    }

    @Override
    public long stored(String guildId, String typeId, int quality) {
        return j.one("SELECT amount FROM guild_storage WHERE guild_id = ? AND type_id = ? AND quality = ?", args(guildId, typeId, quality),
                rs -> rs.getLong(1), 0L);
    }

    @Override
    public void setStored(String guildId, String typeId, int quality, long amount) {
        j.update("INSERT INTO guild_storage (guild_id, type_id, quality, amount) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (guild_id, type_id, quality) DO UPDATE SET amount = excluded.amount", args(guildId, typeId, quality, amount));
    }

    @Override
    public boolean logStorage(String key, String guildId, String uuid, String typeId, int quality, long delta, long day, long at) {
        return j.update("INSERT OR IGNORE INTO guild_storage_log (request_key, guild_id, uuid, type_id, quality, delta, day, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                args(key, guildId, uuid, typeId, quality, delta, day, at)) > 0;
    }

    @Override
    public long withdrawnOn(String guildId, String uuid, long day) {
        return j.one("SELECT COALESCE(-SUM(delta), 0) FROM guild_storage_log WHERE guild_id = ? AND uuid = ? AND day = ? AND delta < 0",
                args(guildId, uuid, day), rs -> rs.getLong(1), 0L);
    }

    // ------------------------------------------------------------------ 길드 의뢰
    @Override
    public List<GuildQuestRow> guildQuests(String guildId, long week) {
        return j.query("SELECT quest_id, progress, done_at FROM guild_quest WHERE guild_id = ? AND week = ?", args(guildId, week),
                rs -> new GuildQuestRow(rs.getString(1), rs.getLong(2), rs.getLong(3)));
    }

    @Override
    public long addGuildQuestProgress(String guildId, long week, String questId, long delta) {
        j.update("INSERT INTO guild_quest (guild_id, week, quest_id, progress) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (guild_id, week, quest_id) DO UPDATE SET progress = progress + excluded.progress", args(guildId, week, questId, delta));
        return j.one("SELECT progress FROM guild_quest WHERE guild_id = ? AND week = ? AND quest_id = ?", args(guildId, week, questId), rs -> rs.getLong(1), 0L);
    }

    @Override
    public boolean finishGuildQuest(String guildId, long week, String questId, long at) {
        return j.update("UPDATE guild_quest SET done_at = ? WHERE guild_id = ? AND week = ? AND quest_id = ? AND done_at = 0", args(at, guildId, week, questId)) > 0;
    }

    @Override
    public void addContribution(String guildId, long week, String questId, String uuid, long amount) {
        j.update("INSERT INTO guild_quest_contrib (guild_id, week, quest_id, uuid, amount) VALUES (?, ?, ?, ?, ?) "
                + "ON CONFLICT (guild_id, week, quest_id, uuid) DO UPDATE SET amount = amount + excluded.amount", args(guildId, week, questId, uuid, amount));
    }

    @Override
    public List<Contribution> contributions(String guildId, long week, String questId) {
        return j.query("SELECT uuid, amount FROM guild_quest_contrib WHERE guild_id = ? AND week = ? AND quest_id = ? ORDER BY amount DESC",
                args(guildId, week, questId), rs -> new Contribution(rs.getString(1), rs.getLong(2)));
    }

    // ------------------------------------------------------------------ 레이드
    @Override
    public boolean lockedOut(String uuid, String raidId, long week) {
        return j.one("SELECT 1 FROM raid_lockout WHERE uuid = ? AND raid_id = ? AND week = ?", args(uuid, raidId, week), rs -> true, false);
    }

    @Override
    public boolean addLockout(String uuid, String raidId, long week) {
        return j.update("INSERT OR IGNORE INTO raid_lockout (uuid, raid_id, week) VALUES (?, ?, ?)", args(uuid, raidId, week)) > 0;
    }

    @Override
    public void insertClear(RaidClear c) {
        j.update("INSERT OR IGNORE INTO raid_clear (run_id, raid_id, leader, members, duration_ms, at) VALUES (?, ?, ?, ?, ?, ?)",
                args(c.runId(), c.raidId(), c.leader(), c.members(), c.durationMs(), c.at()));
    }

    @Override
    public List<RaidClear> bestClears(String raidId, int limit) {
        return j.query("SELECT run_id, raid_id, leader, members, duration_ms, at FROM raid_clear WHERE raid_id = ? ORDER BY duration_ms, at LIMIT ?",
                args(raidId, limit), rs -> new RaidClear(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getLong(6)));
    }

    @Override
    public int clearsOf(String uuid) {
        return j.one("SELECT COUNT(*) FROM raid_clear WHERE members LIKE ?", args("%" + uuid + "%"), rs -> rs.getInt(1), 0);
    }

    // ------------------------------------------------------------------ 대형 조각
    private static final String ART = "SELECT id, owner, kind, title, world, x, y, z, yaw, quality, materials, views, created_at FROM artwork ";

    private static Artwork art(java.sql.ResultSet rs) throws SQLException {
        return new Artwork(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getInt(7),
                rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getString(11), rs.getLong(12), rs.getLong(13));
    }

    @Override
    public List<Artwork> artworks() {
        return j.query(ART + "ORDER BY created_at", args(), JdbcAdventureRepository::art);
    }

    @Override
    public Optional<Artwork> artwork(String id) {
        return Optional.ofNullable(j.one(ART + "WHERE id = ?", args(id), JdbcAdventureRepository::art, null));
    }

    @Override
    public void insertArtwork(Artwork a) {
        j.update("INSERT INTO artwork (id, owner, kind, title, world, x, y, z, yaw, quality, materials, views, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                args(a.id(), a.owner(), a.kind(), a.title(), a.world(), a.x(), a.y(), a.z(), a.yaw(), a.quality(), a.materials(), a.views(), a.createdAt()));
    }

    @Override
    public void deleteArtwork(String id) {
        j.update("DELETE FROM artwork WHERE id = ?", args(id));
        j.update("DELETE FROM artwork_view WHERE artwork_id = ?", args(id));
    }

    @Override
    public void renameArtwork(String id, String title) {
        j.update("UPDATE artwork SET title = ? WHERE id = ?", args(title, id));
    }

    @Override
    public boolean viewArtwork(String artworkId, String uuid, long day) {
        return j.update("INSERT OR IGNORE INTO artwork_view (artwork_id, uuid, day) VALUES (?, ?, ?)", args(artworkId, uuid, day)) > 0;
    }

    @Override
    public void addViews(String artworkId, long delta) {
        j.update("UPDATE artwork SET views = views + ? WHERE id = ?", args(delta, artworkId));
    }
}
