package io.versaera.domain.origin;

import io.versaera.domain.common.DomainException;

/**
 * 시작 도시 (content/origins.yml). 원작: 처음 접속하면 스타팅 도시에서 소환되고, 한 달(게임 시간) 동안 성 · 도시 밖으로 못 나간다.
 *
 * @param region 도시 지역 id (그 안과 하위 지역만 다닐 수 있다)
 */
public record StartCity(String id, String name, String region, String kingdom, String source, String note) {
    public StartCity {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "city.bad_id", "시작 도시 id 형식: " + id);
        DomainException.require(region != null && !region.isBlank(), "city.no_region", "시작 도시 지역 없음: " + id);
        note = note == null ? "" : note;
    }
}
