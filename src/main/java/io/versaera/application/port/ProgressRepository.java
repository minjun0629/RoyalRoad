package io.versaera.application.port;

import java.util.List;

import java.util.Map;
import java.util.Optional;

/** 숙련 · 행동 기록 · 발견 · 최초 발견 · NPC 관계 · 히든 해금 (플레이어 성장 기록) */
public interface ProgressRepository {
    long masteryXp(String uuid, String discipline);

    void setMasteryXp(String uuid, String discipline, long xp);

    Map<String, Long> allMastery(String uuid);

    long counter(String uuid, String key);

    /** @return 더한 뒤 값 */
    long addCounter(String uuid, String key, long delta);

    Map<String, Long> allCounters(String uuid);

    /** @return 새로 기록했으면 true (이미 발견했으면 false) */
    boolean discover(String uuid, String kind, String ref, long at);

    boolean discovered(String uuid, String kind, String ref);

    /** 그 종류로 발견한 것들 (먼저 발견한 순) */
    List<String> discoveries(String uuid, String kind);

    /** 종류 → 발견 수 */
    Map<String, Integer> discoveryCounts(String uuid);

    /** 이 사람이 차지한 서버 최초 발견 수 */
    int worldFirsts(String uuid);

    record WorldFirst(String uuid, String name, long at) {}

    /** 서버 최초 발견 자리 차지. 이미 누가 했으면 false */
    boolean claimWorldFirst(String kind, String ref, String uuid, String name, long at);

    Optional<WorldFirst> worldFirst(String kind, String ref);

    record RelationRow(int affinity, long lastTalk) {}

    RelationRow relation(String uuid, String npc);

    void setRelation(String uuid, String npc, int affinity, long lastTalk);

    boolean unlockHidden(String uuid, String ruleId, long at);

    boolean hiddenUnlocked(String uuid, String ruleId);
}
