package io.versaera.application;

import io.versaera.application.port.AuditLog;
import io.versaera.application.port.ItemRepository;
import io.versaera.application.port.MarketRepository;
import io.versaera.application.port.MarketRepository.Listing;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.common.Money;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.item.Custody;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.market.MarketCatalog;
import io.versaera.domain.market.MarketPricing;

import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * 지역 경매장 (TRD-02). 경매장은 시장마다 따로 있다 (지역 시세 차이가 남도록).
 * <ul>
 *   <li>고유 아이템: 올리는 순간 custody 가 ESCROW("auction:&lt;id&gt;") — 인벤토리 · 거래 · 다른 경매에 동시에 있을 수 없다</li>
 *   <li>묶음 재료: 플랫폼이 인벤토리에서 빼서 넘긴다. 실패하면 배달함으로 돌려준다</li>
 *   <li>구매: 목록 상태를 OPEN→SOLD 로 조건부 갱신(한 명만 성공) + 돈 이동 + 수수료 + 아이템 이동을 한 트랜잭션에서</li>
 *   <li>취소 · 만료: 물건은 판매자의 배달함으로</li>
 * </ul>
 */
public final class AuctionService {
    public static final long DURATION_MS = 48L * 3600 * 1000;
    public static final int MAX_OPEN = 20;
    public static final long MAX_PRICE = 1_000_000_000L;
    public static final String SINK = "system:sink";

    private final TxRunner tx;
    private final MarketRepository repo;
    private final ItemRepository itemRepo;
    private final ItemService items;
    private final EconomyService economy;
    private final MarketCatalog catalog;
    private final AuditLog audit;
    private final EventBus bus;
    private final GameClock clock;
    private final ToDoubleFunction<String> feeCut;

    public AuctionService(TxRunner tx, MarketRepository repo, ItemRepository itemRepo, ItemService items, EconomyService economy, MarketCatalog catalog,
                          AuditLog audit, EventBus bus, GameClock clock, ToDoubleFunction<String> feeCut) {
        this.tx = tx;
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.items = items;
        this.economy = economy;
        this.catalog = catalog;
        this.audit = audit;
        this.bus = bus;
        this.clock = clock;
        this.feeCut = feeCut;
    }

    private static String escrow(String id) {
        return "auction:" + id;
    }

    private void checkNew(String uuid, String market, long price) {
        catalog.market(market);
        DomainException.require(price > 0 && price <= MAX_PRICE, "auction.bad_price", "가격은 1 ~ " + MAX_PRICE + " 골드");
        DomainException.require(repo.bySeller(uuid, "OPEN").size() < MAX_OPEN, "auction.too_many", "동시에 " + MAX_OPEN + "개까지 올릴 수 있습니다");
    }

    public Listing listUnique(String uuid, String market, String itemId, long price) {
        Listing l = tx.inTx(() -> {
            checkNew(uuid, market, price);
            ItemInstance it = itemRepo.find(itemId).orElseThrow(() -> DomainException.of("item.unknown", "없는 아이템"));
            DomainException.require(it.custody().equals(Custody.player(uuid)), "item.not_owner", "가진 아이템만 올릴 수 있습니다");
            String id = UUID.randomUUID().toString();
            it.custody(Custody.escrow(escrow(id)));
            itemRepo.update(it);
            itemRepo.history(itemId, "AUCTION_LISTED", uuid, market + " " + price, clock.nowMillis());
            long now = clock.nowMillis();
            Listing x = new Listing(id, uuid, market, "UNIQUE", itemId, it.typeId(), it.quality(), 1, price, "OPEN", null, now, now + DURATION_MS, null);
            repo.insert(x);
            audit.record("AUCTION_LISTED", uuid, id, it.typeId() + " " + price, null);
            return x;
        });
        return l;
    }

    /** 플랫폼이 인벤토리에서 먼저 뺀 묶음 재료를 올린다 (실패하면 배달함으로 환불) */
    public Listing listBulk(String uuid, String market, String typeId, int quality, int amount, long price) {
        try {
            ItemType t = items.types().get(typeId);
            DomainException.require(!t.category().unique(), "auction.unique", "장비는 하나씩 올립니다");
            DomainException.require(amount > 0 && amount <= 64 * 9, "auction.bad_amount", "수량이 잘못되었습니다");
            return tx.inTx(() -> {
                checkNew(uuid, market, price);
                long now = clock.nowMillis();
                Listing x = new Listing(UUID.randomUUID().toString(), uuid, market, "BULK", null, typeId, quality, amount, price, "OPEN", null, now,
                        now + DURATION_MS, null);
                repo.insert(x);
                audit.record("AUCTION_LISTED", uuid, x.id(), typeId + " x" + amount + " " + price, null);
                return x;
            });
        } catch (RuntimeException e) {
            if (amount > 0 && amount <= 64 * 36 && items.types().has(typeId))
                items.deliverBulk(uuid, typeId, quality, amount, "auction_refund");
            throw e;
        }
    }

    public List<Listing> browse(String market, String typeId, int limit) {
        catalog.market(market);
        return repo.open(market, typeId, Math.max(1, Math.min(100, limit)));
    }

    public List<Listing> mine(String uuid) {
        return repo.bySeller(uuid, "OPEN");
    }

    /** 사기. 같은 물건을 두 사람이 동시에 사도 한 명만 성공한다 */
    public Listing buy(String buyer, String listingId) {
        AfterCommit after = new AfterCommit();
        Listing done = tx.inTx(() -> {
            Listing l = repo.find(listingId).orElseThrow(() -> DomainException.of("auction.unknown", "없는 매물입니다"));
            DomainException.require(l.state().equals("OPEN") && l.expiresAt() > clock.nowMillis(), "auction.closed", "이미 끝난 매물입니다");
            DomainException.require(!l.seller().equals(buyer), "auction.self", "자기 물건은 살 수 없습니다");
            DomainException.require(repo.close(l.id(), "OPEN", "SOLD", buyer, clock.nowMillis()), "auction.closed", "이미 끝난 매물입니다");
            long fee = MarketPricing.auctionFee(l.price(), catalog.market(l.market()).tax(), feeCut.applyAsDouble(l.seller()));
            Money.requirePositive(l.price());
            economy.transferInTx(buyer, l.seller(), l.price(), "auction_buy", "auction:" + l.id() + ":pay", after);
            if (fee > 0) economy.transferInTx(l.seller(), SINK, fee, "auction_fee", "auction:" + l.id() + ":fee", after);
            if (l.kind().equals("UNIQUE")) {
                ItemInstance it = itemRepo.find(l.itemId()).orElseThrow();
                DomainException.require(it.custody().equals(Custody.escrow(escrow(l.id()))), "auction.item_moved", "매물 상태가 바뀌었습니다");
                it.custody(Custody.delivery(buyer));
                itemRepo.update(it);
                itemRepo.history(it.id(), "AUCTION_SOLD", buyer, l.seller() + " " + l.price(), clock.nowMillis());
            } else {
                items.deliverBulk(buyer, l.typeId(), l.quality(), l.amount(), "auction:" + l.id());
            }
            audit.record("AUCTION_SOLD", buyer, l.id(), l.seller() + " " + l.price() + " fee=" + fee, null);
            after.add(new GameEvents.AuctionSold(l.id(), l.seller(), buyer, l.price()));
            return l;
        });
        after.publish(bus);
        return done;
    }

    public void cancel(String uuid, String listingId) {
        tx.inTx(() -> {
            Listing l = repo.find(listingId).orElseThrow(() -> DomainException.of("auction.unknown", "없는 매물입니다"));
            DomainException.require(l.seller().equals(uuid), "auction.not_owner", "자기 매물만 내릴 수 있습니다");
            DomainException.require(repo.close(l.id(), "OPEN", "CANCELLED", null, clock.nowMillis()), "auction.closed", "이미 끝난 매물입니다");
            giveBack(l, "AUCTION_CANCELLED");
            return null;
        });
    }

    /** 만료 처리 (주기적으로) — 돌려준 개수 */
    public int expire(int limit) {
        return tx.inTx(() -> {
            int n = 0;
            for (Listing l : repo.expired(clock.nowMillis(), limit))
                if (repo.close(l.id(), "OPEN", "EXPIRED", null, clock.nowMillis())) {
                    giveBack(l, "AUCTION_EXPIRED");
                    n++;
                }
            return n;
        });
    }

    private void giveBack(Listing l, String why) {
        if (l.kind().equals("UNIQUE")) {
            ItemInstance it = itemRepo.find(l.itemId()).orElseThrow();
            if (!it.custody().equals(Custody.escrow(escrow(l.id())))) return;
            it.custody(Custody.delivery(l.seller()));
            itemRepo.update(it);
            itemRepo.history(it.id(), why, l.seller(), l.id(), clock.nowMillis());
        } else {
            items.deliverBulk(l.seller(), l.typeId(), l.quality(), l.amount(), why.toLowerCase());
        }
        audit.record(why, l.seller(), l.id(), null, null);
    }
}
