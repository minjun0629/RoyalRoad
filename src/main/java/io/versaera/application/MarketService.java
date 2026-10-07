package io.versaera.application;

import io.versaera.application.port.AuditLog;
import io.versaera.application.port.ItemRepository;
import io.versaera.application.port.MarketRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.item.Custody;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.market.MarketCatalog;
import io.versaera.domain.market.MarketPricing;

import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * NPC 상점 · 지역 시세 (TRD-02). NPC 가 주고받는 돈은 시스템 지갑("system:npc")을 거쳐 원장에 남는다.
 * 플레이어가 NPC 에게 팔면 그 시장의 공급이 늘어 값이 내려가고, 시간이 지나면 돌아온다.
 */
public final class MarketService {
    public static final String NPC_WALLET = "system:npc";

    public record Quote(String typeId, int quality, long buy, long sell) {}

    private final TxRunner tx;
    private final MarketRepository repo;
    private final ItemRepository itemRepo;
    private final ItemService items;
    private final EconomyService economy;
    private final MarketCatalog catalog;
    private final AuditLog audit;
    private final EventBus bus;
    private final GameClock clock;
    private final ToDoubleFunction<String> discount;

    public MarketService(TxRunner tx, MarketRepository repo, ItemRepository itemRepo, ItemService items, EconomyService economy, MarketCatalog catalog,
                         AuditLog audit, EventBus bus, GameClock clock, ToDoubleFunction<String> discount) {
        this.tx = tx;
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.items = items;
        this.economy = economy;
        this.catalog = catalog;
        this.audit = audit;
        this.bus = bus;
        this.clock = clock;
        this.discount = discount;
        for (String t : catalog.prices().keySet()) items.types().get(t);
        for (MarketCatalog.Shop s : catalog.shops().values())
            for (MarketCatalog.Offer o : s.sells()) {
                items.types().get(o.typeId());
                catalog.base(o.typeId());
            }
    }

    private ToDoubleFunction<String> regionDiscount = r -> 0;

    /** 지역 할인 (대상단 도착 같은 월드 이벤트) */
    public void regionDiscount(ToDoubleFunction<String> byRegion) {
        this.regionDiscount = byRegion;
    }

    private double discountFor(String uuid, MarketCatalog.Market m) {
        return discount.applyAsDouble(uuid) + regionDiscount.applyAsDouble(m.region());
    }

    private java.util.function.ToDoubleBiFunction<String, String> npcDiscount = (u, n) -> 0;

    /** NPC 와의 관계 · 그 지역 번영에 따른 할인 (NpcWorldService) — 적대면 예외를 던져 거래를 막는다 */
    public void npcDiscount(java.util.function.ToDoubleBiFunction<String, String> f) {
        this.npcDiscount = f;
    }

    /** 이 NPC 에게서 살 때의 값 (메뉴에 보이는 값 = 실제로 내는 값) */
    public Quote quoteAt(String uuid, String npcId, String typeId, int quality) {
        MarketCatalog.Market m = catalog.market(shop(npcId).market());
        long base = catalog.base(typeId), sup = supply(m.id(), typeId);
        double r = region(m, typeId);
        return new Quote(typeId, quality, MarketPricing.buyPrice(base, r, sup, quality, discountFor(uuid, m) + npcDiscount.applyAsDouble(uuid, npcId)),
                MarketPricing.sellPrice(base, r, sup, quality));
    }

    /** 지역 경제: NPC 생산자 · 소비자가 시장의 공급을 움직인다 */
    public void adjustSupply(String market, String typeId, long delta) {
        catalog.market(market);
        tx.inTx(() -> {
            repo.setSupply(market, typeId, supply(market, typeId) + delta, clock.nowMillis());
            return null;
        });
    }

    public MarketCatalog catalog() {
        return catalog;
    }

    public MarketCatalog.Shop shop(String npcId) {
        MarketCatalog.Shop s = catalog.shops().get(npcId);
        if (s == null) throw DomainException.of("shop.none", "이 사람은 물건을 팔지 않습니다");
        return s;
    }

    /** 지금 공급 (시간 감쇠 반영) */
    public long supply(String market, String typeId) {
        MarketRepository.Supply s = repo.supply(market, typeId);
        return MarketPricing.decayed(s.supply(), clock.nowMillis() - s.updatedAt());
    }

    private double region(MarketCatalog.Market m, String typeId) {
        return MarketPricing.regionMult(m, items.types().get(typeId).tags());
    }

    public Quote quote(String uuid, String market, String typeId, int quality) {
        MarketCatalog.Market m = catalog.market(market);
        long base = catalog.base(typeId), sup = supply(market, typeId);
        double r = region(m, typeId);
        return new Quote(typeId, quality, MarketPricing.buyPrice(base, r, sup, quality, discountFor(uuid, m)),
                MarketPricing.sellPrice(base, r, sup, quality));
    }

    /** NPC 에게서 산다. 묶음은 배달함으로, 장비는 새 고유 아이템으로 (NPC 품질) */
    public long buy(String uuid, String npcId, int offerIndex, int amount, String requestId) {
        MarketCatalog.Shop shop = shop(npcId);
        DomainException.require(offerIndex >= 0 && offerIndex < shop.sells().size(), "shop.bad_offer", "없는 물건입니다");
        DomainException.require(amount > 0 && amount <= 64, "shop.bad_amount", "한 번에 1~64개까지 살 수 있습니다");
        MarketCatalog.Offer o = shop.sells().get(offerIndex);
        ItemType t = items.types().get(o.typeId());
        DomainException.require(!t.category().unique() || amount <= 4, "shop.bad_amount", "장비는 한 번에 4개까지");
        MarketCatalog.Market m = catalog.market(shop.market());
        AfterCommit after = new AfterCommit();
        String key = "shop_buy:" + requestId;
        long paid = tx.inTx(() -> {
            long sup = supply(m.id(), o.typeId());
            long total = MarketPricing.buyTotal(catalog.base(o.typeId()), region(m, o.typeId()), sup, o.quality(), amount,
                    discountFor(uuid, m) + npcDiscount.applyAsDouble(uuid, npcId));
            if (!economy.transferInTx(uuid, NPC_WALLET, total, "shop_buy", key, after)) return 0L;   // 같은 요청 반복
            if (t.category().unique())
                for (int i = 0; i < amount; i++) items.createInTx(o.typeId(), o.quality(), null, npcId, "shop", Map.of(), uuid, key + ":" + i, after);
            else items.deliverBulk(uuid, o.typeId(), o.quality(), amount, "shop:" + npcId);
            repo.setSupply(m.id(), o.typeId(), sup - amount, clock.nowMillis());
            return total;
        });
        after.publish(bus);
        return paid;
    }

    /**
     * 묶음 재료를 NPC 에게 판다. 플랫폼이 인벤토리에서 먼저 빼서 넘기고, 실패하면 배달함으로 돌려준다.
     */
    public long sellBulk(String uuid, String npcId, String typeId, int quality, int amount, String requestId) {
        boolean[] done = {false};
        try {
            long got = doSellBulk(uuid, npcId, typeId, quality, amount, requestId);
            done[0] = true;
            return got;
        } finally {
            if (!done[0] && amount > 0 && amount <= 64 * 36) items.deliverBulk(uuid, typeId, quality, amount, "shop_refund");
        }
    }

    private long doSellBulk(String uuid, String npcId, String typeId, int quality, int amount, String requestId) {
        MarketCatalog.Shop shop = shop(npcId);
        ItemType t = items.types().get(typeId);
        DomainException.require(!t.category().unique(), "shop.unique", "장비는 하나씩 팝니다");
        DomainException.require(amount > 0 && amount <= 64 * 36, "shop.bad_amount", "수량이 잘못되었습니다");
        DomainException.require(t.tags().stream().anyMatch(shop.buys()::contains), "shop.not_buying", "이 사람은 그 물건을 사지 않습니다");
        MarketCatalog.Market m = catalog.market(shop.market());
        AfterCommit after = new AfterCommit();
        long got = tx.inTx(() -> {
            long sup = supply(m.id(), typeId);
            long total = MarketPricing.sellTotal(catalog.base(typeId), region(m, typeId), sup, quality, amount);
            if (total > 0) economy.depositInTx(uuid, total, "shop_sell", "shop_sell:" + requestId, after);
            repo.setSupply(m.id(), typeId, sup + amount, clock.nowMillis());
            audit.record("SHOP_SELL", uuid, npcId, typeId + " x" + amount + " q=" + quality + " = " + total, requestId);
            return total;
        });
        after.publish(bus);
        return got;
    }

    /** 고유 아이템(장비 · 예술품)을 NPC 에게 판다 — 아이템은 파괴된다 */
    public long sellUnique(String uuid, String npcId, String itemId, String requestId) {
        MarketCatalog.Shop shop = shop(npcId);
        AfterCommit after = new AfterCommit();
        long got = tx.inTx(() -> {
            ItemInstance it = itemRepo.find(itemId).orElseThrow(() -> DomainException.of("item.unknown", "없는 아이템"));
            DomainException.require(it.custody().equals(Custody.player(uuid)), "item.not_owner", "가진 아이템이 아닙니다");
            ItemType t = items.types().get(it.typeId());
            DomainException.require(t.tags().stream().anyMatch(shop.buys()::contains), "shop.not_buying", "이 사람은 그 물건을 사지 않습니다");
            MarketCatalog.Market m = catalog.market(shop.market());
            double wear = it.maxDurability() == 0 ? 1 : Math.max(0.2, (double) it.durability() / Math.max(1, it.maxDurability()));
            long total = (long) Math.floor(MarketPricing.sellPrice(catalog.base(t.id()), region(m, t.id()), supply(m.id(), t.id()), it.quality()) * wear);
            it.custody(Custody.destroyed());
            itemRepo.update(it);
            itemRepo.history(itemId, "SOLD_TO_NPC", uuid, npcId, clock.nowMillis());
            if (total > 0) economy.depositInTx(uuid, total, "shop_sell", "shop_sell_u:" + itemId, after);
            audit.record("SHOP_SELL", uuid, itemId, t.id() + " = " + total, requestId);
            return total;
        });
        after.publish(bus);
        return got;
    }
}
