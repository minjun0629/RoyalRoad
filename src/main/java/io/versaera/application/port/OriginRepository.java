package io.versaera.application.port;

import java.util.Optional;

/** 캐릭터 출신 (종족 · 성별 · 시작 도시). (V4 의 login_lock 표는 접속 제한을 없애면서 쓰지 않는다) */
public interface OriginRepository {
    record Origin(String uuid, String race, String gender, String city, long createdAt) {}

    Optional<Origin> find(String uuid);

    /** @return 새로 만들었으면 true (이미 있으면 false — 출신은 바꿀 수 없다) */
    boolean create(Origin o);
}
