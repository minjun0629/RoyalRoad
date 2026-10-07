package io.versaera.domain.realm;

import io.versaera.domain.world.Region;

import java.util.Set;

/**
 * 땅 · 성 · 공성 규칙 (원작 개념: 땅을 사고, 성을 차지하고, 국가를 세우고, 대륙을 통일한 첫 황제가 된다). 수치는 모두 ORIGINAL.
 * <ul>
 *   <li>땅: 청크(16×16) 단위로 산다. 금역(위험 6) · 던전 입구 · 명소 · 문 · 바다에서는 못 산다. 도시 안은 비싸다</li>
 *   <li>성: 도시 · 성 · 전초기지 지역 하나가 성 하나. 길드가 사서(시작 도시는 수도라 비쌈) 매일 수입을 받고, 성 안 개인 상점 거래에 세금을 매긴다</li>
 *   <li>공성: 선포하면 30분. 공격 길드원이 성 한가운데(8 블록 안)에 있고 수비 길드원이 없으면 1초씩 쌓여 300초가 되면 성이 넘어간다</li>
 * </ul>
 */
public final class RealmRules {
    public static final int MAX_PLOTS = 16, MAX_SHOPS = 5, MAX_STOCK = 27;
    public static final long SIEGE_MS = 30 * 60_000L, SIEGE_COST = 5_000;
    public static final int CAPTURE_SECONDS = 300, CAPTURE_RADIUS = 8, MAX_TAX = 20;
    private static final Set<String> NO_LAND = Set.of("dungeon_site", "landmark", "portal", "wall", "sea", "hole", "sky");

    private RealmRules() {
    }

    public static boolean town(Region r) {
        return r != null && r.maxY() >= 64 && (r.tags().contains("city") || r.tags().contains("fortress") || r.tags().contains("outpost"));
    }

    /** 땅 값 (못 사면 -1) */
    public static long plotPrice(Region r) {
        if (r == null || r.danger() >= 6) return -1;
        for (String t : r.tags()) if (NO_LAND.contains(t)) return -1;
        if (town(r)) return 5_000 + r.danger() * 500L;
        return 500 + r.danger() * 300L;
    }

    /** 땅을 팔면 산 값의 절반 */
    public static long refund(long price) {
        return price / 2;
    }

    /** 성 값: 수도(시작 도시) > 성 > 도시 > 전초기지 */
    public static long castlePrice(Region r, boolean capital) {
        if (capital) return 100_000;
        if (r.tags().contains("fortress")) return 50_000;
        if (r.tags().contains("city")) return 30_000;
        return 15_000;
    }

    /** 성의 하루 수입 (현실 하루) = 성 값의 1% */
    public static long dailyIncome(long castlePrice) {
        return castlePrice / 100;
    }

    /** 공성 진행: 공격 측만 있으면 +1초, 수비 측이 있으면 멈춤 (밀어내야 한다) */
    public static int captureTick(int progress, int attackersNear, int defendersNear) {
        if (attackersNear > 0 && defendersNear == 0) return Math.min(CAPTURE_SECONDS, progress + 1);
        return progress;
    }

    /** 상점 거래의 성 세금 */
    public static long tax(long amount, int pct) {
        return amount * Math.max(0, Math.min(MAX_TAX, pct)) / 100;
    }
}
