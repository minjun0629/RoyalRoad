package io.versaera.persistence;

import io.versaera.application.port.ResetRepository;

import java.util.List;

/**
 * 플레이어 초기화: 그 사람의 성장 · 돈 · 아이템 · 관계 · 펫 · 탈것 · 땅 · 상점 · 작품 · 발견 기록을 지운다.
 * 그 사람이 가진 서버 최초 기록도 지워 최초 보상을 다시 받을 수 있게 하고, 장부의 한 번만 받는 보상 열쇠(업적 · 퀘스트 · 보스 …)를 풀어
 * 다시 해내면 다시 받는다. 장부 줄 · 감사 로그 · 보스 기여 · 레이드 공략 기록 자체는 남긴다.
 */
public final class JdbcResetRepository implements ResetRepository {
    /** uuid 열로 지우는 표 */
    static final List<String> BY_UUID = List.of("player_profile", "wallet", "mastery", "action_counter", "discovery", "npc_relation", "delivery_bulk",
            "hidden_unlock", "player_job", "quest_progress", "reputation", "guild_member", "death_log", "map_explored", "character_origin", "login_lock",
            "plot_member", "npc_memory", "player_title", "travel_journey", "raid_lockout", "artwork_view", "guild_quest_contrib");

    private final Jdbc j;

    public JdbcResetRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public String guildLedBy(String uuid) {
        return j.one("SELECT id FROM guild WHERE leader = ?", ps -> ps.setString(1, uuid), rs -> rs.getString(1), null);
    }

    @Override
    public int wipe(String uuid) {
        int n = 0;
        // 가진 물건: 땅 · 상점 · 경매 · 펫 · 탈것 · 작품
        n += j.update("DELETE FROM plot_member WHERE (world, cx, cz) IN (SELECT world, cx, cz FROM land_plot WHERE owner = ?)", ps -> ps.setString(1, uuid));
        n += j.update("DELETE FROM land_plot WHERE owner = ?", ps -> ps.setString(1, uuid));
        n += j.update("DELETE FROM shop_stock WHERE shop_id IN (SELECT id FROM player_shop WHERE owner = ?)", ps -> ps.setString(1, uuid));
        n += j.update("DELETE FROM player_shop WHERE owner = ?", ps -> ps.setString(1, uuid));
        n += j.update("DELETE FROM auction_listing WHERE seller = ? AND state = 'OPEN'", ps -> ps.setString(1, uuid));
        for (String t : List.of("pet", "mount", "artwork")) n += j.update("DELETE FROM " + t + " WHERE owner = ?", ps -> ps.setString(1, uuid));
        // 고유 아이템: 그 사람이 들고 있거나 배달함 · 경매 · 상점에 맡긴 것
        n += j.update("DELETE FROM item_history WHERE item_id IN (SELECT id FROM item_instance WHERE custody_ref = ?)", ps -> ps.setString(1, uuid));
        n += j.update("DELETE FROM item_instance WHERE custody_ref = ?", ps -> ps.setString(1, uuid));
        for (String t : BY_UUID) n += j.update("DELETE FROM " + t + " WHERE uuid = ?", ps -> ps.setString(1, uuid));
        // 서버 최초 (지역 · 보스 · 업적 …) → 다음에 처음 해낸 사람이 다시 최초가 된다
        n += j.update("DELETE FROM world_first WHERE uuid = ?", ps -> ps.setString(1, uuid));
        // 한 번만 받는 보상 열쇠: 기록은 남기고 열쇠만 푼다
        n += j.update("UPDATE ledger SET idempotency_key = NULL WHERE idempotency_key LIKE ?", ps -> ps.setString(1, "%" + uuid + "%"));
        return n;
    }
}
