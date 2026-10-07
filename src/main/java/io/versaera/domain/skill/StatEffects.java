package io.versaera.domain.skill;

import java.util.Map;

/**
 * 행동 스탯 → 실제 효과 (SKL-03, 수치 ORIGINAL). 모두 상한이 있어 한 스탯이 게임을 지배하지 않는다.
 */
public record StatEffects(double maxHealthPct, double priceDiscount, int fishingBonusLevels, double craftVarianceMult, boolean seesHints,
                          double meleeDamagePct) {
    public static StatEffects of(Map<String, Integer> pts) {
        int endurance = pts.getOrDefault("endurance", 0), charm = pts.getOrDefault("charm", 0), sea = pts.getOrDefault("seafaring", 0),
                steady = pts.getOrDefault("steady_hand", 0), insight = pts.getOrDefault("insight", 0), strength = pts.getOrDefault("strength", 0);
        return new StatEffects(Math.min(0.30, endurance * 0.01), Math.min(0.15, charm * 0.005), Math.min(6, sea / 5),
                Math.max(0.4, 1 - steady * 0.03), insight >= 1, Math.min(0.15, strength * 0.005));
    }

    public static final StatEffects NONE = new StatEffects(0, 0, 0, 1, false, 0);
}
