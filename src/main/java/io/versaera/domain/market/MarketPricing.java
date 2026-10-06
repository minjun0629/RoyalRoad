package io.versaera.domain.market;

import java.util.Set;

/**
 * 시세 계산 (TRD-02, 수치 ORIGINAL). 순수 함수.
 * <ul>
 *   <li>지역: 그 시장에서 흔한 물건은 ×0.8, 귀한 물건은 ×1.3 → 지역 사이 교역에 이유가 생긴다</li>
 *   <li>공급: 플레이어가 NPC 에게 판 만큼 공급이 늘어 값이 내려가고(최저 ×0.5), 사 가면 올라간다(최고 ×1.6). 시간마다 0 으로 돌아간다</li>
 *   <li>품질: 품질 500 = ×1.0, 0 = ×0.6, 1000 = ×1.4</li>
 *   <li>NPC 판매가는 기준 ×1.25, 매입가는 ×0.6 → 같은 곳에서 사서 되파는 무한 이득은 없다</li>
 * </ul>
 */
public final class MarketPricing {
    public static final double BUY_MARKUP = 1.25, SELL_RATE = 0.6, MAX_DISCOUNT = 0.25;

    private MarketPricing() {
    }

    public static double regionMult(MarketCatalog.Market m, Set<String> tags) {
        boolean cheap = tags.stream().anyMatch(m.cheap()::contains), dear = tags.stream().anyMatch(m.dear()::contains);
        return cheap && !dear ? 0.8 : dear && !cheap ? 1.3 : 1.0;
    }

    public static double supplyMult(long supply) {
        return Math.max(0.5, Math.min(1.6, 1 - supply * 0.004));
    }

    public static double qualityMult(int quality) {
        return 0.6 + Math.max(0, Math.min(1000, quality)) / 1000.0 * 0.8;
    }

    /** 공급이 시간에 따라 0 으로 돌아감 (한 시간에 10%) */
    public static long decayed(long supply, long elapsedMs) {
        if (supply == 0 || elapsedMs <= 0) return supply;
        double hours = elapsedMs / 3_600_000.0;
        return Math.round(supply * Math.pow(0.9, hours));
    }

    /** NPC 에게서 하나 살 때 값 (할인은 최대 25%) */
    public static long buyPrice(long base, double regionMult, long supply, int quality, double discount) {
        double d = Math.max(0, Math.min(MAX_DISCOUNT, discount));
        return Math.max(1, (long) Math.ceil(base * regionMult * supplyMult(supply) * qualityMult(quality) * BUY_MARKUP * (1 - d)));
    }

    /** NPC 에게 하나 팔 때 값 */
    public static long sellPrice(long base, double regionMult, long supply, int quality) {
        return Math.max(0, (long) Math.floor(base * regionMult * supplyMult(supply) * qualityMult(quality) * SELL_RATE));
    }

    /** 여러 개를 팔 때: 하나 팔 때마다 공급이 늘어 값이 내려간다 */
    public static long sellTotal(long base, double regionMult, long supply, int quality, int amount) {
        long sum = 0;
        for (int i = 0; i < amount; i++) sum += sellPrice(base, regionMult, supply + i, quality);
        return sum;
    }

    public static long buyTotal(long base, double regionMult, long supply, int quality, int amount, double discount) {
        long sum = 0;
        for (int i = 0; i < amount; i++) sum += buyPrice(base, regionMult, supply - i, quality, discount);
        return sum;
    }

    /** 경매 수수료 (상인 직업은 일부 감면) */
    public static long auctionFee(long price, double tax, double feeCut) {
        double c = Math.max(0, Math.min(1, feeCut));
        return (long) Math.floor(price * tax * (1 - c));
    }
}
