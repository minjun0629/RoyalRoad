package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.death.DeathPenalty;
import io.versaera.domain.origin.Gender;
import io.versaera.domain.reputation.Reputation;
import io.versaera.domain.skill.Mastery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 원작 규칙: 캐릭터 만들기 · 초보 기간 · 원작식 사망 · 명성 · 악명 · 살인자 · 신전 */
class CanonRulesTest {
    @Test
    void characterIsMadeOnceAndStartsConfinedInTheCity() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertTrue(w.s.origins.character(p).isEmpty());
            var c = w.s.origins.create(p, "dwarf", Gender.NEUTRAL, "serabourg");
            assertEquals("드워프", c.race().name());
            assertEquals(Gender.NEUTRAL, c.gender());
            assertThrows(DomainException.class, () -> w.s.origins.create(p, "elf", Gender.FEMALE, "aren"), "출신은 바꿀 수 없다");
            // 보리빵 10개 (원작: 처음엔 보리빵 10개뿐)
            assertTrue(w.s.items.pendingBulk(p).stream().anyMatch(b -> b.typeId().equals("barley_bread") && b.amount() == 10));
            // 초보 기간: 게임 30일 = 시간 4배면 현실 7.5일
            assertTrue(c.beginner(w.now.get()));
            assertEquals(w.now.get() + (long) (7.5 * 86_400_000L), c.beginnerUntil());
            assertTrue(w.s.origins.insideCity(c, "serabourg"));
            assertTrue(w.s.origins.insideCity(c, "basic_training_hall"), "도시 안 수련관은 다닐 수 있다");
            assertFalse(w.s.origins.insideCity(c, "rosenheim"), "성문 밖은 못 나간다");
            assertFalse(w.s.origins.insideCity(c, "harden"));
            w.now.addAndGet((long) (7.5 * 86_400_000L) + 1);
            assertFalse(w.s.origins.character(p).orElseThrow().beginner(w.now.get()), "한 달(게임 시간)이 지나면 나갈 수 있다");
        }
    }

    @Test
    void raceBonusSpeedsItsDisciplines() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String dwarf = TestWorld.player(), human = TestWorld.player();
            w.s.origins.create(dwarf, "dwarf", Gender.MALE, "aren");
            w.s.origins.create(human, "human", Gender.FEMALE, "aren");
            long d = w.s.growth.addXp(dwarf, "smithing", 100, 1).gained(), h = w.s.growth.addXp(human, "smithing", 100, 1).gained();
            assertTrue(d > h, "드워프는 대장 기술이 빠르다: " + d + " vs " + h);
        }
    }

    @Test
    void beginnersDieWithoutPenaltyThenCanonDeathBites() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            w.s.origins.create(p, "human", Gender.MALE, "serabourg");
            long xp = Mastery.cumulative(6) + 10;
            w.s.tx.inTx(() -> {
                w.s.progress.setMasteryXp(p, "mining", xp);
                return null;
            });
            var b = w.s.deaths.die(p, "serabourg", 0, List.of());
            assertTrue(b.beginner());
            assertEquals(xp, w.s.growth.xp(p, "mining"), "초보는 사망 페널티가 없다");
            assertTrue(w.s.origins.activeLock(p).isEmpty());

            w.now.addAndGet(8L * 86_400_000L);
            var o = w.s.deaths.die(p, "fallen_crater", 4, List.of());
            assertFalse(o.beginner());
            assertEquals(5, w.s.growth.level(p, "mining"), "원작식: 진행도가 모자라면 레벨이 떨어진다");
            assertTrue(o.penalty().drops() >= 1, "무작위 아이템 드롭");
            assertEquals(w.now.get() + 24 * 3_600_000L, o.lockUntil(), "현실 24시간 접속 불가");
            assertTrue(w.s.origins.activeLock(p).isPresent());
            w.now.addAndGet(24 * 3_600_000L);
            assertTrue(w.s.origins.activeLock(p).isEmpty(), "24시간 뒤에는 들어올 수 있다");
        }
    }

    @Test
    void murderersSufferAndCanBeKilledFreelyAndCleansed() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player(), c = TestWorld.player();
            var k = w.s.reputation.playerKilled(a, b);
            assertEquals(100, k.notorietyGain());
            var sa = w.s.reputation.standing(a);
            assertTrue(sa.murderer() && sa.notoriety() == 100, "사람을 죽이면 붉은 이름");
            assertEquals(0, w.s.reputation.playerKilled(c, a).notorietyGain(), "살인자를 죽이면 페널티 없음");
            assertFalse(w.s.reputation.standing(c).murderer());
            // 사망 페널티가 더 크다
            assertTrue(Reputation.deathMult(sa.notoriety(), true) >= 2);
            Map<String, Long> m = Map.of("mining", Mastery.cumulative(10));
            assertTrue(DeathPenalty.computeCanon(m, 2, 2).totalXpLoss() > DeathPenalty.computeCanon(m, 2, 1).totalXpLoss());
            // 몬스터 사냥으로 씻긴다
            w.s.reputation.monsterKilled(a);
            assertEquals(99, w.s.reputation.standing(a).notoriety());
            // 신전 기부: 신전 밖에선 안 되고, 안에서는 악명을 씻고 0 이 되면 살인자도 풀린다
            w.s.economy.deposit(a, 100_000, "test", "t1");
            assertThrows(DomainException.class, () -> w.s.reputation.donate(a, "rosenheim", 1000, "d0"));
            var d = w.s.reputation.donate(a, "basic_training_hall", 5000, "d1");
            assertEquals("가이아", d.god().name(), "세라보그 성의 주신 신전 (하위 지역에서도)");
            assertEquals(99, d.cleansed());
            var after = w.s.reputation.standing(a);
            assertEquals(0, after.notoriety());
            assertFalse(after.murderer());
            assertTrue(d.blessingSeconds() > 0, "남은 돈은 축복으로");
            assertThrows(DomainException.class, () -> w.s.reputation.donate(a, "serabourg", 5000, "d1"), "같은 기부는 한 번만");
        }
    }

    @Test
    void evilNpcsOnlyDealWithTheNotoriousAndFameRaisesRewards() {
        assertTrue(Reputation.npcRefuses(false, 100, false));
        assertTrue(Reputation.npcRefuses(false, 0, true), "살인자에게는 보통 NPC 가 일을 맡기지 않는다");
        assertFalse(Reputation.npcRefuses(false, 99, false));
        assertTrue(Reputation.npcRefuses(true, 0, false), "악한 NPC 는 착한 사람을 상대하지 않는다");
        assertFalse(Reputation.npcRefuses(true, 150, false));
        assertTrue(Reputation.questRewardMult(5_000, 0) > Reputation.questRewardMult(0, 0));
        assertTrue(Reputation.questRewardMult(0, 200) < 1);
        assertTrue(Reputation.relaxesRequirements(20_000) && !Reputation.relaxesRequirements(999));
        assertEquals("명사", Reputation.fameName(5_000));
    }
}
