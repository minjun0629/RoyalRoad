package io.versaera.domain.item;

import io.versaera.domain.common.DomainException;

import java.util.Map;
import java.util.Set;

/**
 * 아이템 종류 정의 (content/items.yml). 실제 아이템 하나하나는 {@link ItemInstance}.
 *
 * @param material   보여 줄 Minecraft 재질 이름 (Bukkit 없이 문자열로)
 * @param stats      공격력 · 방어력 등 (품질에 따라 배율이 붙음)
 * @param requires   착용 · 사용 조건 (예: "mastery.swordsmanship" → 12, "stat.strength" → 30, "fame" → 500) — {@link ItemOptions#unmet}
 * @param lore       감정하면 보이는 내력 (없으면 null)
 * @param set        세트 id (없으면 null) — {@link ItemSet}
 */
public record ItemType(String id, String name, ItemCategory category, String material, int baseDurability, int weight,
                       Set<String> tags, Map<String, Integer> stats, Map<String, Integer> requires, String source, String lore, String set) {
    public ItemType(String id, String name, ItemCategory category, String material, int baseDurability, int weight,
                    Set<String> tags, Map<String, Integer> stats, Map<String, Integer> requires, String source) {
        this(id, name, category, material, baseDurability, weight, tags, stats, requires, source, null, null);
    }

    public ItemType {
        DomainException.require(id != null && id.matches("[a-z0-9_.]+"), "item.bad_id", "아이템 id 형식이 잘못되었습니다: " + id);
        DomainException.require(baseDurability >= 0 && weight >= 0, "item.bad_numbers", "내구도 · 무게는 음수일 수 없습니다: " + id);
        tags = Set.copyOf(tags == null ? Set.of() : tags);
        stats = Map.copyOf(stats == null ? Map.of() : stats);
        requires = Map.copyOf(requires == null ? Map.of() : requires);
        for (String k : stats.keySet())
            DomainException.require(ItemOptions.KEYS.contains(k), "item.bad_option", "없는 아이템 능력: " + k + " (" + id + ")");
    }

    /** 감정해야 보이는 능력이 있는가 (공격력 · 방어력 말고 다른 능력, 또는 내력) */
    public boolean hasHidden() {
        return lore != null || stats.keySet().stream().anyMatch(ItemOptions::hidden);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
