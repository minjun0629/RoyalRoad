package io.versaera.domain.combat;

import io.versaera.domain.boss.Shape;
import io.versaera.domain.common.DomainException;

/**
 * 스킬 (content/skills.yml). 원작 스킬 이름 · 효과를 복제하지 않은 ORIGINAL 스킬.
 *
 * @param kind     AREA(내 앞 범위) · PROJECTILE(직선 첫 대상) · DASH(돌진 + 범위) · SELF(자기 강화)
 * @param weapon   필요한 무기 태그 (null = 아무 무기)
 * @param resource STAMINA 또는 MANA
 * @param effect   맞은 대상에게 거는 상태 이상 (null = 없음)
 */
public record SkillDefinition(String id, String name, Kind kind, String weapon, String discipline, int minLevel, Resource resource, int cost,
                              long cooldownMs, Shape shape, double radius, double widthOrAngle, double damageMult, StatusEffect effect,
                              int effectSeconds, boolean basic, String source) {
    public enum Kind { AREA, PROJECTILE, DASH, SELF }

    public enum Resource { STAMINA, MANA }

    public SkillDefinition {
        DomainException.require(id != null && id.matches("[a-z_]+"), "skill.bad_id", "스킬 id 형식이 잘못되었습니다: " + id);
        DomainException.require(cost >= 0 && cooldownMs >= 200 && radius >= 0 && damageMult >= 0, "skill.bad_numbers", "스킬 수치가 잘못되었습니다: " + id);
        DomainException.require(kind == Kind.SELF || shape != null || kind == Kind.PROJECTILE, "skill.no_shape", "범위가 없습니다: " + id);
    }
}
