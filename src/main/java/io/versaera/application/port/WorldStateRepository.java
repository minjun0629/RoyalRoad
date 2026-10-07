package io.versaera.application.port;

import java.util.List;

/** NPC 기억 · 지역 번영 (V6) */
public interface WorldStateRepository {
    record Memory(String npcId, String kind, String detail, int weight, long at) {}

    void remember(String uuid, String npcId, String kind, String detail, int weight, long at);

    /** 최근 것부터 */
    List<Memory> memories(String uuid, String npcId, int limit);

    /** 이 NPC 에 대해 같은 종류의 기억이 몇 번 있나 */
    int count(String uuid, String npcId, String kind);

    record Prosperity(int value, long updatedAt) {}

    Prosperity prosperity(String region);

    void setProsperity(String region, int value, long at);

    /** @return 처음이면 true (같은 key 로 두 번 오르지 않는다) */
    boolean contribute(String key, String region, int amount, long at);
}
