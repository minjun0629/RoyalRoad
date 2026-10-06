package io.versaera.domain.world;

import io.versaera.domain.common.DomainException;

/**
 * 다른 땅 · 다른 차원으로 건너가는 문 (content/gates.yml). 문 지역에 들어서면 조건을 보고 도착점으로 옮긴다.
 * 조건은 탐험 숙련 레벨 하나뿐 — 확률도, 원작의 숨겨진 조건(배 재료 · 퀘스트 해법)도 쓰지 않는다.
 *
 * @param region         들어서면 문이 작동하는 지역 id
 * @param minExploration 필요한 탐험 숙련 레벨 (1 = 누구나)
 */
public record Gate(String id, String name, String region, String toWorld, int toX, int toZ, int minExploration, String source, String note) {
    public Gate {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "gate.bad_id", "문 id 형식이 잘못되었습니다: " + id);
        DomainException.require(region != null && !region.isBlank() && toWorld != null && !toWorld.isBlank(), "gate.bad_target", "문 지역 · 도착 세계가 없습니다: " + id);
        DomainException.require(minExploration >= 1 && minExploration <= 31, "gate.bad_level", "탐험 레벨은 1 ~ 31: " + id);
        note = note == null ? "" : note;
    }
}
