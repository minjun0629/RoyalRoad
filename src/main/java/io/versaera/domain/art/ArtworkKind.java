package io.versaera.domain.art;

import io.versaera.domain.common.DomainException;

import java.util.List;

/**
 * 대형 조각 작품 종류 (ART-02). 여러 재료가 각자 자리(받침 · 몸체 · 장식)를 맡는다 — 받침은 돌, 몸체는 대리석, 장식은 은 … 처럼.
 * 재료마다 모양(색 · 결)이 달라 같은 종류라도 재료 조합에 따라 다른 작품이 된다.
 *
 * @param scale  세상에 세울 때 크기 배율
 * @param level  조각 숙련이 이만큼 있어야 한다
 * @param fame   품질 1000 일 때 만든 사람이 얻는 명성 (품질에 비례)
 */
public record ArtworkKind(String id, String name, double scale, int level, List<Part> parts, int fame, String desc) {
    /** slot: pedestal · body · accent, tags: 이 자리에 쓸 수 있는 재료 태그 (하나라도 맞으면), amount: 개수 */
    public record Part(String slot, List<String> tags, int amount) {
        public Part {
            DomainException.require(List.of("pedestal", "body", "accent").contains(slot), "art.bad_slot", "자리는 pedestal · body · accent: " + slot);
            DomainException.require(amount >= 1 && amount <= 64 && !tags.isEmpty(), "art.bad_part", "재료 수 · 태그: " + slot);
            tags = List.copyOf(tags);
        }
    }

    public ArtworkKind {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "art.bad_id", "작품 id 형식: " + id);
        DomainException.require(scale >= 0.5 && scale <= 6 && level >= 0 && level <= io.versaera.domain.skill.Mastery.MAX_LEVEL && fame >= 0, "art.bad_kind", "작품 수치: " + id);
        DomainException.require(parts.size() >= 2, "art.too_few", "대형 작품은 재료 자리가 두 곳 이상: " + id);
        DomainException.require(parts.stream().map(Part::slot).distinct().count() == parts.size(), "art.dup_slot", "같은 자리가 두 번: " + id);
        parts = List.copyOf(parts);
    }

    /**
     * 품질 (0~1000): 재료 품질을 개수로 가중 평균 → 숙련(요구치 넘는 만큼 레벨당 +1%, 최대 +30%) · 예술 스탯(포인트당 +0.5%, 최대 +15%)
     * → 손 떨림(roll 0~1 → ±8%, 정밀 스탯이 있으면 절반)
     */
    public int quality(List<int[]> materialQualityAndAmount, int sculptingLevel, int artistry, boolean steady, double roll) {
        long sum = 0, n = 0;
        for (int[] qa : materialQualityAndAmount) {
            sum += (long) qa[0] * qa[1];
            n += qa[1];
        }
        double base = n == 0 ? 0 : (double) sum / n;
        double mult = 1 + Math.min(0.30, Math.max(0, sculptingLevel - level) * 0.01) + Math.min(0.15, artistry * 0.005);
        double wobble = (roll * 2 - 1) * (steady ? 0.04 : 0.08);
        return (int) Math.max(0, Math.min(1000, Math.round(base * mult * (1 + wobble))));
    }
}
