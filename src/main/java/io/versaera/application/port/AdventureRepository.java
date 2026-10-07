package io.versaera.application.port;

import java.util.List;
import java.util.Optional;

/** V7 저장소: 칭호 · 펫 · 탈것 · 여행 · 길드 창고/의뢰 · 레이드 · 대형 조각 */
public interface AdventureRepository {
    // ---- 칭호
    Optional<String> title(String uuid);

    void setTitle(String uuid, String title, long at);

    void clearTitle(String uuid);

    // ---- 펫
    record Pet(String id, String owner, String species, String name, int level, long xp, int loyalty, long fedAt, long faintedUntil,
               long createdAt) {}

    List<Pet> pets(String owner);

    Optional<Pet> pet(String id);

    void insertPet(Pet p);

    void updatePet(Pet p);

    void releasePet(String id);

    // ---- 탈것
    record Mount(String id, String owner, String kind, String name, long createdAt) {}

    List<Mount> mounts(String owner);

    void insertMount(Mount m);

    // ---- 여행
    record Journey(String uuid, String route, String dest, long departAt, long arriveAt) {}

    Optional<Journey> journey(String uuid);

    /** @return 새로 떠났으면 true (이미 여행 중이면 false) */
    boolean startJourney(Journey j);

    void endJourney(String uuid);

    // ---- 길드 창고
    record Stored(String typeId, int quality, long amount) {}

    List<Stored> storage(String guildId);

    long stored(String guildId, String typeId, int quality);

    void setStored(String guildId, String typeId, int quality, long amount);

    /** @return 새 기록이면 true (같은 요청 key 면 false) */
    boolean logStorage(String key, String guildId, String uuid, String typeId, int quality, long delta, long day, long at);

    /** 그날 그 사람이 꺼낸 양 (양수) */
    long withdrawnOn(String guildId, String uuid, long day);

    // ---- 길드 의뢰
    record GuildQuestRow(String questId, long progress, long doneAt) {}

    List<GuildQuestRow> guildQuests(String guildId, long week);

    long addGuildQuestProgress(String guildId, long week, String questId, long delta);

    /** @return 이번에 끝냈으면 true */
    boolean finishGuildQuest(String guildId, long week, String questId, long at);

    void addContribution(String guildId, long week, String questId, String uuid, long amount);

    record Contribution(String uuid, long amount) {}

    List<Contribution> contributions(String guildId, long week, String questId);

    // ---- 레이드
    boolean lockedOut(String uuid, String raidId, long week);

    boolean addLockout(String uuid, String raidId, long week);

    record RaidClear(String runId, String raidId, String leader, String members, long durationMs, long at) {}

    void insertClear(RaidClear c);

    List<RaidClear> bestClears(String raidId, int limit);

    int clearsOf(String uuid);

    // ---- 대형 조각
    record Artwork(String id, String owner, String kind, String title, String world, int x, int y, int z, int yaw, int quality,
                   String materials, long views, long createdAt) {}

    List<Artwork> artworks();

    Optional<Artwork> artwork(String id);

    void insertArtwork(Artwork a);

    void deleteArtwork(String id);

    void renameArtwork(String id, String title);

    /** @return 오늘 처음 감상했으면 true */
    boolean viewArtwork(String artworkId, String uuid, long day);

    void addViews(String artworkId, long delta);
}
