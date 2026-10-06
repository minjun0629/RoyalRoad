package io.versaera.domain.market;

import io.versaera.domain.common.DomainException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 시장 · 기본 시세 · NPC 상점 정의 (content/market.yml, 모두 ORIGINAL) */
public record MarketCatalog(Map<String, Market> markets, Map<String, Long> prices, Map<String, Shop> shops) {
    /** cheap: 이 시장에서 흔한 태그(싸다) · dear: 귀한 태그(비싸다) · tax: 경매장 수수료 */
    public record Market(String id, String name, String region, double tax, Set<String> cheap, Set<String> dear) {
        public Market {
            DomainException.require(tax >= 0 && tax <= 0.3, "market.bad_tax", "수수료는 0~30%: " + id);
        }
    }

    public record Offer(String typeId, int quality) {}

    /** buys: 이 상인이 사 주는 아이템 태그 */
    public record Shop(String npcId, String market, List<Offer> sells, Set<String> buys) {}

    public static final MarketCatalog EMPTY = new MarketCatalog(Map.of(), Map.of(), Map.of());

    public Market market(String id) {
        Market m = markets.get(id);
        if (m == null) throw DomainException.of("market.unknown", "없는 시장: " + id);
        return m;
    }

    public long base(String typeId) {
        Long p = prices.get(typeId);
        if (p == null) throw DomainException.of("market.no_price", "시세가 없는 물건: " + typeId);
        return p;
    }
}
