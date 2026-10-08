package io.versaera.application.port;

import java.util.Map;
import java.util.Optional;

/** 진행 중 상태 저장소 (runtime_state) — 범위(scope) · 키마다 한 줄 */
public interface RuntimeStateRepository {
    record Row(String scope, String key, String data, long expiresAt, long updatedAt) {}

    void put(String scope, String key, String data, long expiresAt, long now);

    Optional<Row> get(String scope, String key);

    /** 범위 안의 모든 줄 (키 → 줄) */
    Map<String, Row> all(String scope);

    void delete(String scope, String key);

    /** 만료된 줄 지우기 · @return 지운 수 */
    int purge(long now);
}
