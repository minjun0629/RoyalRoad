package io.versaera.domain.potion;

/**
 * 물약 규칙 (CANON 개념): 물약은 즉시 회복이 아니라 <b>회복력을 잠시 올리는</b> 것이고, 여러 개를 이어 마셔 겹칠 수 없다.
 * 레벨이 높을수록 같은 물약의 효율이 떨어진다 (원작: 400대쯤엔 의미 있는 물약이 없다). 수치는 ORIGINAL.
 *
 * <p>물약 등급 t 는 숙련 레벨 t×10 까지 온전히 듣고, 그 위로 10 레벨마다 지속 시간이 절반이 된다.
 */
public final class PotionRule {
    public record Effect(int amplifier, int seconds) {
        public boolean none() {
            return seconds <= 0;
        }
    }

    private PotionRule() {
    }

    /** @param tier 물약 등급 (1 ~ 5) · @param level 마시는 사람의 가장 높은 전투 숙련 레벨 (1 ~ 31) */
    public static Effect effect(int tier, int level) {
        int t = Math.max(1, Math.min(5, tier));
        int base = 20 + t * 10;   // 30 ~ 70초
        int over = Math.max(0, level - t * 10);
        int seconds = (int) Math.floor(base / Math.pow(2, Math.ceil(over / 10.0)));
        if (seconds < 5) seconds = 0;
        return new Effect(t >= 3 ? 1 : 0, seconds);
    }

    /** 앞의 물약 효과가 남아 있으면 마실 수 없다 */
    public static boolean canDrink(long now, long activeUntil) {
        return now >= activeUntil;
    }
}
