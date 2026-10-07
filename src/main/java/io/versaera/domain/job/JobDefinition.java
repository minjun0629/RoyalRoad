package io.versaera.domain.job;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.hidden.Condition;

import java.util.List;
import java.util.Map;

/**
 * 직업 (content/jobs.yml). 직업 이름(검사 · 기사 · 궁수 · 암살자 · 마법사)은 원작에서 확인된 CANON 이지만,
 * <b>전직 조건 · 효과는 모두 ORIGINAL</b> — 원작의 전직 · 히든 직업 조건을 쓰지 않는다.
 * 레벨이 아니라 숙련 · 행동 기록 · 관계 조합으로 열린다 (레벨 만능 금지).
 *
 * @param slot   COMBAT(전투 직업 1개) 또는 LIFE(생활 직업 1개) — 둘을 동시에 가질 수 있다
 * @param parent 상위 전직이면 먼저 가져야 하는 직업
 * @param perks  attack_pct · defense_pct · max_health_pct · crit · stamina · mana · craft_quality · gather_bonus · price_discount …
 * @param skills 이 직업이 쓸 수 있게 되는 스킬 id
 */
public record JobDefinition(String id, String name, String slot, int tier, String parent, Condition requires, Map<String, Double> perks,
                            List<String> skills, String source) {
    public JobDefinition {
        DomainException.require(id != null && id.matches("[a-z_]+"), "job.bad_id", "직업 id 형식이 잘못되었습니다: " + id);
        DomainException.require("COMBAT".equals(slot) || "LIFE".equals(slot), "job.bad_slot", "slot 은 COMBAT 또는 LIFE: " + id);
        DomainException.require(requires != null, "job.no_requirement", "전직 조건이 없습니다: " + id);
        perks = Map.copyOf(perks == null ? Map.of() : perks);
        skills = List.copyOf(skills == null ? List.of() : skills);
    }

    public double perk(String key) {
        return perks.getOrDefault(key, 0.0);
    }
}
