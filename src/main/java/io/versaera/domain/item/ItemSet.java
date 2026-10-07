package io.versaera.domain.item;

import io.versaera.domain.common.DomainException;

import java.util.Map;
import java.util.TreeMap;

/**
 * 세트 아이템 (content/items.yml 의 sets). 같은 세트를 여러 개 입으면 그 개수의 보너스가 붙는다 (아래 단계도 함께).
 *
 * @param bonuses 입은 개수 → 더해지는 능력 (ItemOptions.KEYS)
 */
public record ItemSet(String id, String name, Map<Integer, Map<String, Integer>> bonuses, String source) {
    public ItemSet {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "set.bad_id", "세트 id 형식: " + id);
        DomainException.require(bonuses != null && !bonuses.isEmpty(), "set.empty", "보너스 없는 세트: " + id);
        TreeMap<Integer, Map<String, Integer>> b = new TreeMap<>();
        bonuses.forEach((n, m) -> {
            DomainException.require(n >= 2, "set.bad_count", "세트 보너스는 2개부터: " + id);
            for (String k : m.keySet()) DomainException.require(ItemOptions.KEYS.contains(k), "set.bad_option", "없는 능력: " + k + " (" + id + ")");
            b.put(n, Map.copyOf(m));
        });
        bonuses = java.util.Collections.unmodifiableMap(b);
    }

    /** 입은 개수에 따라 붙는 보너스 합 */
    public Map<String, Integer> bonusFor(int worn) {
        Map<String, Integer> out = new TreeMap<>();
        bonuses.forEach((n, m) -> { if (worn >= n) m.forEach((k, v) -> out.merge(k, v, Integer::sum)); });
        return out;
    }
}
