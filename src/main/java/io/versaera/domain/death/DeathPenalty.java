package io.versaera.domain.death;

import io.versaera.domain.skill.Mastery;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 사망 페널티 (SOURCE-BASED). 원작은 24시간 접속 불가 · 레벨 · 숙련 하락 · 아이템 드롭이지만,
 * 서버 게임에서는 그대로 쓰면 플레이를 막으므로 완화한다 (ORIGINAL 수치):
 * <ul>
 *   <li>숙련: 각 분야의 <b>지금 단계 진행도</b>만 (3 + 위험도 × 2)% 잃는다 — 레벨은 내려가지 않는다</li>
 *   <li>장비: 입은 고유 장비가 (위험도 × 2 + 2) 닳고, 위험도 4 이상이면 최대 내구도 1 감소</li>
 *   <li>쇠약: 부활 뒤 (60 + 위험도 × 30)초 동안 약해짐</li>
 *   <li>아이템 드롭 · 접속 제한 없음 (복제 · 분쟁 위험 대비 이득이 작음)</li>
 * </ul>
 */
public final class DeathPenalty {
    public record Result(Map<String, Long> xpLoss, int wear, boolean heavyWear, int weakSeconds) {
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
}
