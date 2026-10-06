package io.versaera.application.port;

public interface DungeonRepository {
    void insert(String id, String dungeonId, long seed, String members, long at);

    void setState(String id, String state, long at);

    /** 서버가 꺼질 때 진행 중이던 판을 실패로 (재시작 복구) */
    int failAllActive(long at);
}
