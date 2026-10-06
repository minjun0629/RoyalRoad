package io.versaera.application.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface QuestRepository {
    record Row(String questId, String state, String progress, String choice, int times, long acceptedAt, Long completedAt) {}

    Optional<Row> find(String uuid, String questId);

    List<Row> all(String uuid);

    void save(String uuid, Row row);

    int reputation(String uuid, String faction);

    void addReputation(String uuid, String faction, int delta);

    Map<String, Integer> reputations(String uuid);
}
