package io.versaera.domain.pet;

import io.versaera.domain.common.DomainException;

import java.util.Map;
import java.util.Set;

/**
 * 길들일 수 있는 동물 (PET-01).
 *
 * @param entity     마인크래프트 엔티티 종류 (WOLF · FOX · CAT · PARROT …)
 * @param tameLevel  길들이기 숙련이 이만큼 있어야 시도할 수 있다
 * @param food       먹이 태그 (손에 든 재료의 태그 중 하나)
 * @param chance     기본 성공 확률 (숙련 · 먹이 품질로 오른다)
 * @param habitat    사는 지역 태그 (비면 어디든)
 * @param skills     레벨 → 펫 스킬 (BITE · GUARD · HOWL · SCAVENGE · MEND · SPOT)
 */
public record Species(String id, String name, String entity, int tameLevel, Set<String> food, double chance, Set<String> habitat,
                      int health, int attack, double healthPerLevel, double attackPerLevel, Map<Integer, String> skills) {
    public static final Set<String> SKILLS = Set.of("BITE", "GUARD", "HOWL", "SCAVENGE", "MEND", "SPOT");

    public Species {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "pet.bad_id", "펫 id 형식: " + id);
        DomainException.require(entity != null && entity.matches("[A-Z_]+"), "pet.bad_entity", "엔티티 이름은 대문자: " + id);
        DomainException.require(chance > 0 && chance <= 1 && tameLevel >= 0 && tameLevel <= io.versaera.domain.skill.Mastery.MAX_LEVEL, "pet.bad_tame", "길들이기 수치: " + id);
        DomainException.require(!food.isEmpty(), "pet.no_food", "먹이가 없습니다: " + id);
        DomainException.require(health > 0 && attack >= 0, "pet.bad_stat", "능력치: " + id);
        for (String s : skills.values()) DomainException.require(SKILLS.contains(s), "pet.bad_skill", "없는 펫 스킬 " + s + ": " + id);
        food = Set.copyOf(food);
        habitat = Set.copyOf(habitat);
        skills = Map.copyOf(skills);
    }

    public int maxHealth(int level) {
        return (int) Math.round(health + healthPerLevel * (level - 1));
    }

    public double attack(int level) {
        return attack + attackPerLevel * (level - 1);
    }

    /** 이 레벨에서 쓸 수 있는 스킬 */
    public Set<String> skillsAt(int level) {
        java.util.TreeSet<String> out = new java.util.TreeSet<>();
        skills.forEach((lv, s) -> { if (level >= lv) out.add(s); });
        return out;
    }
}
