package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.item.Custody;
import io.versaera.domain.realm.RealmRules;
import io.versaera.domain.world.Region;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 땅 · 개인 상점 · 성 · 공성 · 국가 · 황제 */
class RealmServiceTest {
    private static int[] chunkOf(TestWorld w, String region, int dx) {
        Region r = w.s.regions.byId(region);
        return new int[]{Math.floorDiv(r.minX(), 16) + 2 + dx, Math.floorDiv(r.minZ(), 16) + 2};
    }

    @Test
    void landIsBoughtOnceProtectedAndSoldForHalf() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            w.s.economy.deposit(a, 100_000 * io.versaera.domain.economy.Money.SILVER, "test", "a");
            w.s.economy.deposit(b, 100_000 * io.versaera.domain.economy.Money.SILVER, "test", "b");
            int[] c = chunkOf(w, "rosenheim_frontier", 0);
            long price = w.s.realm.plotPrice("world", c[0], c[1]);
            assertTrue(price > 0);
            w.s.realm.buyPlot(a, "world", c[0], c[1], "k1");
            assertEquals(100_000 * io.versaera.domain.economy.Money.SILVER - price, w.s.economy.balance(a));
            assertThrows(DomainException.class, () -> w.s.realm.buyPlot(b, "world", c[0], c[1], "k2"), "주인 있는 땅");
            int[] hole = chunkOf(w, "embinyu_sanctum", 3);
            assertThrows(DomainException.class, () -> w.s.realm.buyPlot(a, "world", hole[0], hole[1], "k3"), "금역은 못 산다");
            assertThrows(DomainException.class, () -> w.s.realm.sellPlot(b, "world", c[0], c[1], "k4"), "남의 땅");
            w.s.realm.trust(a, "world", c[0], c[1], b, true);
            assertEquals(java.util.List.of(b), w.s.realm.members("world", c[0], c[1]));
            assertEquals(price / 2, w.s.realm.sellPlot(a, "world", c[0], c[1], "k5"));
            assertTrue(w.s.realm.plot("world", c[0], c[1]).isEmpty());
        }
    }

    @Test
    void shopHoldsGoodsOnServerAndPaysOwnerAndCastleTax() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String seller = TestWorld.player(), buyer = TestWorld.player();
            w.s.economy.deposit(seller, 100_000 * io.versaera.domain.economy.Money.SILVER, "test", "s");
            w.s.economy.deposit(buyer, 100_000 * io.versaera.domain.economy.Money.SILVER, "test", "b");
            int[] c = chunkOf(w, "harden", 4);
            w.s.realm.buyPlot(seller, "world", c[0], c[1], "p");
            int x = c[0] * 16 + 3, z = c[1] * 16 + 3;
            assertThrows(DomainException.class, () -> w.s.realm.openShop(buyer, "world", x, 70, z, "남의 땅"));
            var shop = w.s.realm.openShop(seller, "world", x, 70, z, "카엘 상회 분점");
            // 고유 아이템 올리기 → ESCROW
            var sword = w.s.items.create("iron_dagger", 600, null, "t", "t", Map.of(), seller, "sw");
            w.s.items.confirmDelivered(sword.id(), seller);
            var st = w.s.realm.stockUnique(seller, shop.id(), sword.id(), 1000);
            assertEquals(Custody.escrow("shop:" + st.id()), w.s.items.find(sword.id()).orElseThrow().custody(), "상점 물건은 서버가 쥔다");
            var bread = w.s.realm.stockBulk(seller, shop.id(), "barley_bread", 400, 10, 5);
            // 성 세금: 하르덴 성을 길드가 가지고 세율 10%
            String lord = TestWorld.player();
            w.s.economy.deposit(lord, 10_000 * io.versaera.domain.economy.Money.SILVER, "test", "l");
            var g = w.s.guilds.create(lord, "하르덴가", "HRD", "g1");
            w.s.economy.deposit(GuildService.wallet(g.id()), 200_000 * io.versaera.domain.economy.Money.SILVER, "test", "gw");
            w.s.realm.buyCastle(lord, "harden", "c1");
            w.s.realm.setTax(lord, "harden", 10);
            long before = w.s.economy.balance(seller);
            w.s.realm.buy(buyer, st.id(), 1, "buy1");
            assertEquals(Custody.delivery(buyer), w.s.items.find(sword.id()).orElseThrow().custody(), "산 물건은 배달함으로");
            assertEquals(before + 900, w.s.economy.balance(seller));
            assertEquals(100_000 * io.versaera.domain.economy.Money.SILVER - 1000, w.s.economy.balance(buyer));
            assertThrows(DomainException.class, () -> w.s.realm.buy(buyer, st.id(), 1, "buy2"), "한 번 팔린 건 끝");
            w.s.realm.buy(buyer, bread.id(), 4, "buy3");
            assertEquals(6, w.s.realm.stock(shop.id()).get(0).amount());
            assertThrows(DomainException.class, () -> w.s.realm.closeShop(seller, shop.id()), "물건이 남으면 못 닫는다");
            w.s.realm.withdrawStock(seller, bread.id());
            w.s.realm.closeShop(seller, shop.id());
            assertThrows(DomainException.class, () -> w.s.realm.sellPlot(TestWorld.player(), "world", c[0], c[1], "x"));
        }
    }

    @Test
    void castlesIncomeSiegeNationsAndTheFirstEmperor() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            w.s.economy.deposit(a, 10_000 * io.versaera.domain.economy.Money.SILVER, "test", "da");
            w.s.economy.deposit(b, 10_000 * io.versaera.domain.economy.Money.SILVER, "test", "db");
            var ga = w.s.guilds.create(a, "아르펜", "ARP", "ga");
            var gb = w.s.guilds.create(b, "하벤", "HVN", "gb");
            w.s.economy.deposit(GuildService.wallet(ga.id()), 2_000_000 * io.versaera.domain.economy.Money.SILVER, "test", "wa");
            w.s.economy.deposit(GuildService.wallet(gb.id()), 2_000_000 * io.versaera.domain.economy.Money.SILVER, "test", "wb");
            assertThrows(DomainException.class, () -> w.s.realm.foundNation(a, "아르펜 왕국"), "성이 없으면 나라를 못 세운다");
            w.s.realm.buyCastle(a, "serabourg", "c1");
            assertThrows(DomainException.class, () -> w.s.realm.buyCastle(b, "serabourg", "c2"), "주인 있는 성은 공성으로");
            var nation = w.s.realm.foundNation(a, "아르펜 왕국");
            // 수입: 하루가 지나면 성 값의 1%
            long wallet = w.s.economy.balance(GuildService.wallet(ga.id()));
            w.now.addAndGet(86_400_000L + 1);
            assertEquals(RealmRules.dailyIncome(100_000 * io.versaera.domain.economy.Money.SILVER), w.s.realm.collectIncome());
            assertEquals(0, w.s.realm.collectIncome(), "같은 날 두 번 주지 않는다");
            assertEquals(wallet + 1000 * io.versaera.domain.economy.Money.SILVER, w.s.economy.balance(GuildService.wallet(ga.id())));
            // 공성: 하벤이 선포 → 전쟁 중엔 악명 없음 → 점령
            var sg = w.s.realm.declareSiege(b, "serabourg", "s1");
            assertTrue(w.s.realm.atWar(ga.id(), gb.id(), "basic_training_hall"), "성 안(하위 지역)은 전쟁터");
            assertFalse(w.s.realm.atWar(ga.id(), gb.id(), "harden"));
            assertThrows(DomainException.class, () -> w.s.realm.declareSiege(b, "serabourg", "s2"), "이미 공성 중");
            assertEquals(RealmRules.CAPTURE_SECONDS, RealmRules.captureTick(RealmRules.CAPTURE_SECONDS - 1, 2, 0));
            assertEquals(10, RealmRules.captureTick(10, 2, 1), "수비가 있으면 멈춤");
            w.s.realm.capture(sg.region());
            assertEquals(gb.id(), w.s.realm.castle("serabourg").orElseThrow().guildId());
            // 황제: 수도 6곳을 모두 가진 나라가 처음 나오면
            w.s.realm.foundNation(b, "하벤 제국");
            java.util.Optional<RealmService.Crowning> crown = java.util.Optional.empty();
            for (String cap : w.s.realm.capitals()) if (!cap.equals("serabourg")) crown = w.s.realm.buyCastle(b, cap, "cap:" + cap);
            assertTrue(crown.isPresent(), "마지막 수도를 가지는 순간 황제");
            assertEquals("하벤 제국", crown.get().nation().name());
            assertEquals(b, w.s.realm.emperor().orElseThrow().leader());
            assertTrue(w.s.items.pendingDeliveries(b).stream().anyMatch(i -> i.typeId().equals("emperor_crown")), "황제의 왕관");
            assertTrue(w.s.realm.checkEmperor().isEmpty(), "황제는 처음 한 번만");
            assertNotEquals(nation.id(), w.s.realm.emperor().orElseThrow().nationId());
        }
    }
}
