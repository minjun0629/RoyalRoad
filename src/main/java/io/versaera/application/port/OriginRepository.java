package io.versaera.application.port;

import java.util.Optional;

/** 캐릭터 출신 (종족 · 성별 · 시작 도시) · 접속 제한 */
public interface OriginRepository {
    record Origin(String uuid, String race, String gender, String city, long createdAt) {}

    record Lock(long until, String reason) {}

    Optional<Origin> find(String uuid);

    /** @return 새로 만들었으면 true (이미 있으면 false — 출신은 바꿀 수 없다) */
    boolean create(Origin o);

    Optional<Lock> lock(String uuid);

    void setLock(String uuid, long until, String reason);

    void clearLock(String uuid);
}
