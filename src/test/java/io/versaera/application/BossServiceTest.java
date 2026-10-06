package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.boss.BossMotion;
import io.versaera.domain.boss.BossRewards;
import io.versaera.domain.boss.BossRewards.Tier;
import io.versaera.domain.common.DomainException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BossServiceTest {
    @Test
    void rewardsFollowContributionNotLastHit() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String tank = TestWorld.player(), dps = TestWorld.player(), leech = TestWorld.player();
            String f = w.s.bosses.start("fallen_colossus");
            w.s.bosses.contribute(f, dps, 40_000, 0, 0);
            w.s.bosses.contribute(f, tank, 5_000, 20_000, 0);
            w.s.bosses.contribute(f, leech, 300, 0, 0);
            var out = w.s.bosses.defeated(f, Map.of(dps, "D"));
            assertEquals(Tier.MVP, out.tiers().get(dps));
            assertEquals(Tier.MVP, out.tiers().get(tank), "막아 낸 피해도 기여");
            assertEquals(Tier.NONE, out.tiers().get(leech), "구경만 한 사람은 보상 없음");
            assertTrue(out.worldFirst());
            long reward = w.s.bosses.boss("fallen_colossus").reward().money();
            assertEquals(reward + reward / 5, w.s.economy.balance(dps));
            assertEquals(0, w.s.economy.balance(leech));
            assertEquals("boss.not_active", assertThrows(DomainException.class, () -> w.s.bosses.defeated(f, Map.of())).code());
            assertEquals(reward + reward / 5, w.s.economy.balance(dps), "두 번 받지 않는다");
            assertEquals(dps, w.s.exploration.worldFirst("boss", "fallen_colossus").orElseThrow().uuid());

            String f2 = w.s.bosses.start("fallen_colossus");
            w.s.bosses.contribute(f2, leech, 100, 0, 0);
            assertFalse(w.s.bosses.defeated(f2, Map.of()).worldFirst(), "최초 처치는 한 번뿐");
        }
    }

    @Test
    void restartFailsOpenFights() throws Exception {
        try (TestWorld w = new TestWorld()) {
            w.s.bosses.start("shard_warden");
            assertEquals(1, w.s.bosses.recover());
        }
    }

    @Test
    void tiersAreShareBased() {
        var t = BossRewards.tiers(Map.of("a", new BossRewards.Contribution(97, 0, 0), "b", new BossRewards.Contribution(3, 0, 0)));
        assertEquals(Tier.PARTICIPANT, t.get("b"));
        assertEquals(Tier.NONE, BossRewards.tiers(Map.of("a", new BossRewards.Contribution(98, 0, 0), "b", new BossRewards.Contribution(2, 0, 0))).get("b"));
    }

    @Test
    void bossTurnsSlowlyKeepsDistanceAndStaysInArena() {
        var p = new BossMotion.Pose(0, 0, 0);
        // 뒤에 있는 대상: 먼저 돌기만 한다 (큰 몸은 천천히)
        var turned = BossMotion.step(p, 0, -20, 0, 0, 40, 10, 3, 8, 0.25);
        assertEquals(0, turned.x(), 1e-9);
        assertEquals(BossMotion.turnRate(8) * 0.25, Math.abs(turned.yaw()), 1e-9);
        // 앞의 대상: 다가가되 판정 반지름 안으로는 들어가지 않는다
        var q = p;
        for (int i = 0; i < 200; i++) q = BossMotion.step(q, 0, 30, 0, 0, 40, 10, 3, 8, 0.25);
        assertEquals(20, q.z(), 1e-6);
        // 전투 공간 밖으로 끌려가지 않는다
        for (int i = 0; i < 400; i++) q = BossMotion.step(q, 0, 500, 0, 0, 40, 10, 3, 8, 0.25);
        assertTrue(Math.hypot(q.x(), q.z()) <= 30 + 1e-6);
    }
}
