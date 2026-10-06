package io.versaera.application.port;

import java.util.Map;

public interface WorldEventRepository {
    /** event_id → 마지막으로 기록한 진행 여부 */
    Map<String, Boolean> states();

    void set(String eventId, boolean active, long startedAt, long nextAt);
}
