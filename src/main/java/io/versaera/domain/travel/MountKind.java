package io.versaera.domain.travel;

import io.versaera.domain.common.DomainException;

/**
 * 탈것 종류 (TRV-01). 마구간지기에게 사거나, 들판의 말을 길들여 얻는다.
 *
 * @param entity     HORSE · DONKEY · MULE · CAMEL · SKELETON_HORSE …
 * @param speed      이동 속도 속성 (말 기본 ≈ 0.225)
 * @param ridingLevel 승마 숙련이 이만큼 있어야 탈 수 있다
 */
public record MountKind(String id, String name, String entity, double speed, double jump, int health, long price, int ridingLevel,
                        String color) {
    public MountKind {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "mount.bad_id", "탈것 id 형식: " + id);
        DomainException.require(entity != null && entity.matches("[A-Z_]+"), "mount.bad_entity", "엔티티 이름은 대문자: " + id);
        DomainException.require(speed > 0 && speed <= 0.5 && jump >= 0 && jump <= 1.2 && health > 0 && price >= 0 && ridingLevel >= 0 && ridingLevel <= io.versaera.domain.skill.Mastery.MAX_LEVEL, "mount.bad_stat", "탈것 능력치: " + id);
    }

    /** 승마 숙련 보너스: 요구치를 넘는 레벨마다 속도 +1% (최대 +15%) */
    public double speedFor(int ridingLevel) {
        return speed * (1 + Math.min(0.15, Math.max(0, ridingLevel - this.ridingLevel) * 0.01));
    }
}
