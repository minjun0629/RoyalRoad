package io.versaera.domain.world;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.item.ItemOptions;

import java.util.*;

/**
 * 들판 몬스터 한 종류 (content/monsters.yml, WLD-05). 이름은 원작에 나온 몬스터(CANON) 또는 이 게임의 것(ORIGINAL),
 * 사는 곳 · 레벨 · 체력 · 전리품은 이 게임이 정한다 (원작에 정확한 서식지가 나온 것은 그 지역에).
 *
 * @param entity  바닐라 몸 (Bukkit EntityType 이름)
 * @param regions 이 지역(과 그 아래 지역)에 산다 — 원작 서식지
 * @param tags    지역 태그가 하나라도 맞고 위험도가 danger 안이면 산다 ("any" = 모든 땅)
 * @param hostile 사람을 보면 먼저 덤빈다 (false 면 맞았을 때만 반격)
 * @param drops   "아이템:확률[:개수]" (품질은 레벨만큼)
 */
public record FieldMonster(String id, String name, String entity, int hp, double damage, int minLevel, int maxLevel,
                           Set<String> regions, Set<String> tags, int minDanger, int maxDanger, boolean hostile, int weight,
                           int minPack, int maxPack, Set<ItemOptions.Kind> kinds, List<Drop> drops, boolean baby, String source, String desc) {
    public record Drop(String item, double chance, int count) {
    }

    public static final Set<String> SOURCES = Set.of("CANON", "SOURCE-BASED", "ORIGINAL");

    public FieldMonster {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "mob.bad_id", "몬스터 id 형식: " + id);
        DomainException.require(hp > 0 && hp <= 2048 && damage >= 0 && minLevel >= 1 && maxLevel >= minLevel && weight > 0
                && minPack >= 1 && maxPack >= minPack && minDanger <= maxDanger, "mob.bad_numbers", "몬스터 수치가 잘못되었습니다: " + id);
        DomainException.require(!regions.isEmpty() || !tags.isEmpty(), "mob.no_habitat", "사는 곳이 없습니다: " + id);
        DomainException.require(SOURCES.contains(source), "mob.bad_source", "출처 분류: " + source + " (" + id + ")");
        regions = Set.copyOf(regions);
        tags = Set.copyOf(tags);
        kinds = Set.copyOf(kinds);
        drops = List.copyOf(drops);
    }

    /** 이 지역에 사는가: 원작 서식지(지역 · 윗지역) 이거나, 태그 · 위험도가 맞을 때 */
    public boolean livesIn(Region r, RegionIndex index) {
        for (Region t = r; t != null; t = t.parent() == null ? null : index.byId(t.parent()))
            if (regions.contains(t.id())) return true;
        if (tags.isEmpty() || r.danger() < minDanger || r.danger() > maxDanger) return false;
        if (tags.contains("any")) return true;
        for (String t : r.tags()) if (tags.contains(t)) return true;
        return false;
    }

    /** 원작 서식지로 정해진 곳인가 (그 지역의 대표 몬스터 — 더 자주 나온다) */
    public boolean home(Region r, RegionIndex index) {
        for (Region t = r; t != null; t = t.parent() == null ? null : index.byId(t.parent()))
            if (regions.contains(t.id())) return true;
        return false;
    }

    /**
     * 이 지역에 나오는 몬스터와 무게. 원작 서식지가 정해진 몬스터가 있으면 그 몬스터가 4 배 자주 나온다.
     * 원작 서식지로만 사는 몬스터가 지역에 있으면, 태그로 사는 흔한 짐승은 절반으로 줄인다.
     */
    public static Map<FieldMonster, Integer> table(Collection<FieldMonster> all, Region r, RegionIndex index) {
        Map<FieldMonster, Integer> out = new LinkedHashMap<>();
        boolean anyHome = false;
        for (FieldMonster m : all) if (m.livesIn(r, index) && m.home(r, index)) anyHome = true;
        for (FieldMonster m : all) {
            if (!m.livesIn(r, index)) continue;
            int w = m.home(r, index) ? m.weight * 4 : anyHome ? Math.max(1, m.weight / 2) : m.weight;
            out.put(m, w);
        }
        // 던전 입구 · 명소처럼 맞는 몬스터가 없는 작은 지역은 둘러싼 지역의 몬스터가 나온다
        if (out.isEmpty() && r.parent() != null && index.byId(r.parent()) != null) return table(all, index.byId(r.parent()), index);
        return out;
    }

    /** 무게대로 하나 고른다 (roll ∈ [0, 1)) */
    public static FieldMonster pick(Map<FieldMonster, Integer> table, double roll) {
        int total = 0;
        for (int w : table.values()) total += w;
        if (total == 0) return null;
        int at = (int) Math.floor(roll * total);
        for (Map.Entry<FieldMonster, Integer> e : table.entrySet()) {
            at -= e.getValue();
            if (at < 0) return e.getKey();
        }
        return table.keySet().iterator().next();
    }
}
