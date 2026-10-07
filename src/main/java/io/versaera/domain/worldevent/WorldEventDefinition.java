package io.versaera.domain.worldevent;

import io.versaera.domain.common.DomainException;

import java.util.Map;

/**
 * 월드 이벤트 정의 (content/world_events.yml, ORIGINAL).
 * @param effects 효과 키 → 값. reveal: "kind:ref" (그동안 입구가 드러남) · gather_bonus.&lt;분야&gt;: 추가 개수 ·
 *                danger: 위험도 가산 · spawn: 엔티티 종류 (희귀 생물) · shop_discount: 그 지역 상점 할인
 */
public record WorldEventDefinition(String id, String name, String region, Kind kind, long periodMs, long durationMs, long jitterMs,
                                   long forecastMs, String forecaster, Map<String, String> effects, String announce, String source) {
    public enum Kind { SANDSTORM, RICH_VEIN, RARE_CREATURE, CARAVAN, FROST_SURGE, SEA_FOG }

    public WorldEventDefinition {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "event.bad_id", "이벤트 id: " + id);
        DomainException.require(periodMs > 0 && durationMs > 0 && durationMs + jitterMs < periodMs, "event.bad_timing",
                "기간 + 흔들림이 주기보다 짧아야 합니다: " + id);
        effects = Map.copyOf(effects == null ? Map.of() : effects);
    }
}
