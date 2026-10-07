package io.versaera.domain.pet;

/** 펫 성장 · 길들이기 · 충성 규칙 (순수 계산) */
public final class PetRules {
    public static final int MAX_LEVEL = 30;
    public static final long FAINT_MS = 5 * 60_000L;

    private PetRules() {
    }

    /** 레벨 n → n+1 에 필요한 경험 */
    public static long xpToNext(int level) {
        return 40L * level * level + 60L * level;
    }

    /** 누적 경험 → 레벨 */
    public static int levelOf(long xp) {
        int lv = 1;
        long need = xpToNext(1);
        while (lv < MAX_LEVEL && xp >= need) {
            xp -= need;
            lv++;
            need = xpToNext(lv);
        }
        return lv;
    }

    /** 데리고 있을 수 있는 수: 2 + 길들이기 8 레벨마다 1 (마스터 31 → 5) */
    public static int maxPets(int tamingLevel) {
        return Math.min(6, 2 + tamingLevel / 8);
    }

    /** 성공 확률: 기본 + 숙련(레벨당 1%, 요구치 넘는 만큼) + 먹이 품질(0~1000 → 최대 +15%), 최대 90% */
    public static double tameChance(Species s, int tamingLevel, int foodQuality) {
        double c = s.chance() + Math.max(0, tamingLevel - s.tameLevel()) * 0.01 + Math.max(0, Math.min(1000, foodQuality)) / 1000.0 * 0.15;
        return Math.min(0.9, c);
    }

    /** 먹이를 준 지 며칠 지났는가에 따라 충성이 떨어진다 (하루 5, 최소 0) */
    public static int loyaltyNow(int loyalty, long fedAt, long now) {
        long days = Math.max(0, now - fedAt) / 86_400_000L;
        return (int) Math.max(0, loyalty - days * 5);
    }

    /** 충성이 낮으면 경험을 덜 얻고 덜 따른다: 0 → 0.5배, 100 → 1.2배 */
    public static double loyaltyMult(int loyalty) {
        return 0.5 + 0.7 * Math.max(0, Math.min(100, loyalty)) / 100.0;
    }
}
