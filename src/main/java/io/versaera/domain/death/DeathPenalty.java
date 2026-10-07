package io.versaera.domain.death;

import io.versaera.domain.skill.Mastery;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 사망 페널티. 두 방식이 있다 (config death.mode):
 * <p><b>canon</b> (기본, CANON): 원작처럼 현실 24시간 접속 불가 · 숙련 레벨까지 떨어짐 · 무작위 아이템 드롭 — {@link #computeCanon}.
 * 악명 · 살인자면 더 크게 (Reputation.deathMult). 초보 기간(성문 밖에 못 나가는 동안)에는 없다.
 * <p><b>soft</b> (ORIGINAL 완화판):
 * <ul>
 *   <li>숙련: 각 분야의 <b>지금 단계 진행도</b>만 (3 + 위험도 × 2)% 잃는다 — 레벨은 내려가지 않는다</li>
 *   <li>장비: 입은 고유 장비가 (위험도 × 2 + 2) 닳고, 위험도 4 이상이면 최대 내구도 1 감소</li>
 *   <li>쇠약: 부활 뒤 (60 + 위험도 × 30)초 동안 약해짐</li>
 *   <li>아이템 드롭 · 접속 제한 없음 (복제 · 분쟁 위험 대비 이득이 작음)</li>
 * </ul>
 */
public final class DeathPenalty {
    public record Result(Map<String, Long> xpLoss, int wear, boolean heavyWear, int weakSeconds, int drops) {
        public Result(Map<String, Long> xpLoss, int wear, boolean heavyWear, int weakSeconds) {
            this(xpLoss, wear, heavyWear, weakSeconds, 0);
        }

        public long totalXpLoss() {
            return xpLoss.values().stream().mapToLong(Long::longValue).sum();
        }
    }

    private DeathPenalty() {
    }

    public static Result compute(Map<String, Long> mastery, int danger) {
        int d = Math.max(0, Math.min(6, danger));
        double pct = (3 + d * 2) / 100.0;
        Map<String, Long> loss = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : mastery.entrySet()) {
            long xp = e.getValue();
            int lv = Mastery.levelOf(xp);
            if (lv >= Mastery.MAX_LEVEL) continue;
            long into = xp - Mastery.cumulative(lv);
            long l = (long) Math.floor(into * pct);
            if (l > 0) loss.put(e.getKey(), l);
        }
        return new Result(loss, d * 2 + 2, d >= 4, 60 + d * 30);
    }

    /**
     * 원작식: 분야마다 <b>지금 레벨 한 칸 크기</b>의 (3 + 위험도 × 2)% × 배율을 잃는다 — 진행도가 모자라면 레벨이 내려간다.
     * 드롭: (1 + 위험도/3) × ⌈배율⌉ 칸, 최대 6. 어느 칸이 떨어질지는 서버가 고른다.
     */
    public static Result computeCanon(Map<String, Long> mastery, int danger, double mult) {
        int d = Math.max(0, Math.min(6, danger));
        double m = Math.max(1, Math.min(4, mult));
        double pct = (3 + d * 2) / 100.0 * m;
        Map<String, Long> loss = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : mastery.entrySet()) {
            long xp = e.getValue();
            int lv = Math.min(Mastery.levelOf(xp), Mastery.MAX_LEVEL - 1);
            long l = Math.min(xp, (long) Math.floor(Mastery.need(lv) * pct));
            if (l > 0) loss.put(e.getKey(), l);
        }
        int drops = canonDrops(d, m);
        return new Result(loss, d * 2 + 2, d >= 4, 60 + d * 30, drops);
    }

    /** 원작식 드롭 칸 수 (플랫폼이 사망 순간에 바로 고를 수 있게 따로) */
    public static int canonDrops(int danger, double mult) {
        int d = Math.max(0, Math.min(6, danger));
        double m = Math.max(1, Math.min(4, mult));
        return Math.min(6, (1 + d / 3) * (int) Math.ceil(m));
    }

    public static final Result NONE = new Result(Map.of(), 0, false, 0, 0);
}
