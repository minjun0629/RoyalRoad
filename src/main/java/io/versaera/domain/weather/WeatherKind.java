package io.versaera.domain.weather;

import io.versaera.domain.common.DomainException;

import java.util.Map;

/**
 * 날씨 한 가지 (WTH-01). 효과는 모두 서버에서 계산한다.
 *
 * @param gather   채집 분야 → 수확 배율 (비 오는 날 낚시 1.3 …)
 * @param melee    근접 피해 배율
 * @param ranged   활 · 투척 피해 배율 (폭풍 · 안개에서 낮다)
 * @param spell    마법 피해 배율
 * @param indoor   NPC 가 집 · 주점으로 들어가는 날씨
 * @param downfall 비 · 눈이 내리게 보인다 (플레이어별 날씨)
 * @param particle 주변에 뿌릴 입자 (안개 · 모래폭풍), 없으면 null
 */
public record WeatherKind(String id, String name, Map<String, Double> gather, double melee, double ranged, double spell, boolean indoor,
                          boolean downfall, String particle, String desc) {
    public WeatherKind {
        DomainException.require(id != null && id.matches("[a-z_]+"), "weather.bad_id", "날씨 id 형식: " + id);
        for (double v : gather.values()) DomainException.require(v >= 0.3 && v <= 2.0, "weather.bad_mult", "채집 배율은 0.3~2.0: " + id);
        DomainException.require(melee >= 0.5 && melee <= 1.5 && ranged >= 0.5 && ranged <= 1.5 && spell >= 0.5 && spell <= 1.5,
                "weather.bad_mult", "전투 배율은 0.5~1.5: " + id);
        gather = Map.copyOf(gather);
    }

    public double gather(String discipline) {
        return gather.getOrDefault(discipline, 1.0);
    }
}
