package io.versaera.application.port;

import io.versaera.domain.boss.BossRewards.Contribution;

import java.util.Map;

public interface BossRepository {
    void start(String fightId, String bossId, long at);

    /** ACTIVE 일 때만 바꾼다. 바꿨으면 true (같은 전투를 두 번 끝내지 않음) */
    boolean finish(String fightId, String state, long at);

    void saveContribution(String fightId, String uuid, Contribution c);

    Map<String, Contribution> contributions(String fightId);

    int failAllActive(long at);
}
