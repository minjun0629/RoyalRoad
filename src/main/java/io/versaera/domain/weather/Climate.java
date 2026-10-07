package io.versaera.domain.weather;

import io.versaera.domain.common.DomainException;

import java.util.Map;
import java.util.Set;

/** 기후: 지역 태그로 고른다 (위에서부터 처음 맞는 것, tags 가 비면 기본). weights = 날씨 → 비중 */
public record Climate(String id, Set<String> tags, Map<String, Integer> weights) {
    public Climate {
        DomainException.require(!weights.isEmpty(), "weather.no_weights", "기후에 날씨가 없습니다: " + id);
        for (int w : weights.values()) DomainException.require(w > 0, "weather.bad_weight", "비중은 양수: " + id);
        tags = Set.copyOf(tags);
        weights = Map.copyOf(weights);
    }
}
