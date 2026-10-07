package io.versaera.domain.origin;

import io.versaera.domain.common.DomainException;

import java.util.Map;

/**
 * 시작 종족 (content/origins.yml). 원작은 시작 종족이 29 → 49가지로 늘었다고만 하고 전체 목록은 없다 —
 * 이 게임은 원작에 시작 종족으로 나온 것만 넣는다 (인간 · 엘프 · 드워프 · 오크 · 조인족).
 *
 * @param xpBonus 분야별 숙련 경험치 배율 더하기 (0.10 = +10%). 수치는 ORIGINAL
 * @param perk    몸의 특성 하나: max_health(최대 체력 +4) · no_fall_damage(낙하 피해 없음) · night_vision · none
 */
public record Race(String id, String name, String source, String note, Map<String, Double> xpBonus, String perk) {
    public static final java.util.Set<String> PERKS = java.util.Set.of("none", "max_health", "no_fall_damage", "night_vision");

    public Race {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "race.bad_id", "종족 id 형식: " + id);
        xpBonus = Map.copyOf(xpBonus == null ? Map.of() : xpBonus);
        perk = perk == null ? "none" : perk;
        DomainException.require(PERKS.contains(perk), "race.bad_perk", "없는 종족 특성: " + perk);
        for (double v : xpBonus.values()) DomainException.require(v >= 0 && v <= 0.25, "race.bad_bonus", "종족 경험치 보너스는 0 ~ 25%: " + id);
        note = note == null ? "" : note;
    }

    public double xpMult(String discipline) {
        return 1 + xpBonus.getOrDefault(discipline, 0.0);
    }
}
