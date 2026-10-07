package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.item.Custody;
import io.versaera.domain.market.MarketCatalog;
import io.versaera.domain.market.MarketPricing;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MarketAuctionTest {
    private static String rich(TestWorld w, long amount) {
        String p = TestWorld.player();
        w.s.economy.deposit(p, amount, "test", "seed:" + p);
        return p;
    }

    @Test
    void regionalPricesDifferAndNoBuySellLoop() {
        var harden = new MarketCatalog.Market("h", "h", "harden", 0.05, Set.of("grain"), Set.of("gem"));
        assertEquals(0.8, MarketPricing.regionMult(harden, Set.of("grain")));
        assertEquals(1.3, MarketPricing.regionMult(harden, Set.of("gem")));
        for (int q = 0; q <= 1000; q += 100)
            for (long sup = -300; sup <= 300; sup += 50)
                assertTrue(MarketPricing.buyPrice(100, 1, sup, q, 1.0) > MarketPricing.sellPrice(100, 1, sup, q),
                        "최대 할인을 받아도 사서 되파는 이득은 없다");
        assertTrue(MarketPricing.sellTotal(100, 1, 0, 500, 100) < 100 * MarketPricing.sellPrice(100, 1, 0, 500), "많이 팔수록 값이 내려간다");
        assertEquals(50, MarketPricing.decayed(100, (long) (Math.log(0.5) / Math.log(0.9) * 3_600_000)), 1);
    }

    @Test
    void npcShopBuyAndSellMoveSupplyAndMoney() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = rich(w, 10_000);
            long paid = w.s.market.buy(p, "kael_trader", 0, 10, "b1");
            assertTrue(paid > 0);
            assertEquals(0, w.s.market.buy(p, "kael_trader", 0, 10, "b1"), "같은 요청은 한 번만");
            assertEquals(10_000 - paid, w.s.economy.balance(p));
            assertEquals(10, w.s.items.pendingBulk(p).get(0).amount());
            assertEquals(-10, w.s.market.supply("harden", "wheat_sheaf"));
            // 장비는 고유 아이템으로
            w.s.market.buy(p, "kael_trader", 3, 1, "b2");
            assertEquals("iron_pickaxe", w.s.items.pendingDeliveries(p).get(0).typeId());
            // 판매: 사지 않는 물건은 환불
            assertEquals("shop.not_buying", assertThrows(DomainException.class, () -> w.s.market.sellBulk(p, "mira_cook", "iron_ore", 500, 5, "s0")).code());
            assertEquals(2, w.s.items.pendingBulk(p).size(), "판 물건이 배달함으로 돌아옴");
            long before = w.s.economy.balance(p);
            long first = w.s.market.sellBulk(p, "oren_smith", "silver_ore", 500, 20, "s1");
            long second = w.s.market.sellBulk(p, "oren_smith", "silver_ore", 500, 20, "s2");
            assertTrue(second < first, "같은 곳에 계속 팔면 값이 떨어진다");
            assertEquals(before + first + second, w.s.economy.balance(p));
            w.now.addAndGet(48L * 3600 * 1000);
            assertTrue(Math.abs(w.s.market.supply("deep_hammer", "silver_ore")) < 3, "시간이 지나면 공급이 회복");
        }
    }

    @Test
    void uniqueSaleDestroysItem() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            var it = w.s.items.create("iron_longsword", 600, null, "x", "test", Map.of(), p, null);
            assertThrows(DomainException.class, () -> w.s.market.sellUnique(p, "oren_smith", it.id(), "u0"), "배달 전 아이템은 못 판다");
            w.s.items.confirmDelivered(it.id(), p);
            assertTrue(w.s.market.sellUnique(p, "oren_smith", it.id(), "u1") > 0);
            assertEquals(Custody.Kind.DESTROYED, w.s.items.find(it.id()).orElseThrow().custody().kind());
            assertThrows(DomainException.class, () -> w.s.market.sellUnique(p, "oren_smith", it.id(), "u2"));
        }
    }

    @Test
    void auctionEscrowsUniqueItemsAndPaysOnce() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String seller = TestWorld.player(), buyer = rich(w, 5000), other = rich(w, 5000);
            var it = w.s.items.create("iron_longsword", 700, seller, "S", "test", Map.of(), seller, null);
            w.s.items.confirmDelivered(it.id(), seller);
            var l = w.s.auctions.listUnique(seller, "harden", it.id(), 1000);
            assertEquals(ItemService.Verdict.IN_ESCROW, w.s.items.validate(it.id(), seller));
            assertThrows(DomainException.class, () -> w.s.auctions.listUnique(seller, "harden", it.id(), 1000), "두 번 올릴 수 없다");
            assertEquals("auction.self", assertThrows(DomainException.class, () -> w.s.auctions.buy(seller, l.id())).code());
            w.s.auctions.buy(buyer, l.id());
            assertEquals("auction.closed", assertThrows(DomainException.class, () -> w.s.auctions.buy(other, l.id())).code());
            assertEquals(4000, w.s.economy.balance(buyer));
            assertEquals(5000, w.s.economy.balance(other));
            assertEquals(1000 - 50, w.s.economy.balance(seller), "5% 수수료");
            assertEquals(ItemService.Verdict.AWAITING_DELIVERY, w.s.items.validate(it.id(), buyer));
        }
    }

    @Test
    void failedPurchaseRollsBackEverything() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String seller = TestWorld.player(), poor = rich(w, 10);
            var l = w.s.auctions.listBulk(seller, "rosaim", "salmon", 500, 20, 400);
            assertEquals("money.insufficient", assertThrows(DomainException.class, () -> w.s.auctions.buy(poor, l.id())).code());
            assertEquals(1, w.s.auctions.browse("rosaim", "salmon", 10).size(), "실패하면 매물은 그대로");
            assertTrue(w.s.items.pendingBulk(poor).isEmpty());
        }
    }

    @Test
    void cancelAndExpiryReturnGoods() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String seller = TestWorld.player();
            var it = w.s.items.create("buckler", 500, seller, "S", "test", Map.of(), seller, null);
            w.s.items.confirmDelivered(it.id(), seller);
            var l = w.s.auctions.listUnique(seller, "harden", it.id(), 300);
            w.s.auctions.cancel(seller, l.id());
            assertEquals(ItemService.Verdict.AWAITING_DELIVERY, w.s.items.validate(it.id(), seller));
            w.s.auctions.listBulk(seller, "harden", "wheat_sheaf", 500, 30, 50);
            assertThrows(DomainException.class, () -> w.s.auctions.listBulk(seller, "nowhere", "wheat_sheaf", 500, 5, 50));
            assertEquals(1, w.s.items.pendingBulk(seller).size(), "잘못 올린 재료는 환불");
            w.now.addAndGet(AuctionService.DURATION_MS + 1);
            assertEquals(1, w.s.auctions.expire(50));
            assertEquals(2, w.s.items.pendingBulk(seller).size());
        }
    }

    @Test
    void cancelAllTakesDownEveryMarketAndReturnsGoods() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String seller = TestWorld.player();
            w.s.auctions.listBulk(seller, "harden", "wheat_sheaf", 500, 10, 50);
            w.s.auctions.listBulk(seller, "rosaim", "salmon", 500, 5, 80);
            assertEquals(2, w.s.auctions.mine(seller).size());
            assertEquals(2, w.s.auctions.cancelAll(seller));
            assertTrue(w.s.auctions.mine(seller).isEmpty());
            assertEquals(2, w.s.items.pendingBulk(seller).size(), "모두 배달함으로");
            assertEquals(0, w.s.auctions.cancelAll(seller));
        }
    }
}
