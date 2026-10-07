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
    void beginnersDieWithoutPenaltyThenCanonDeathTakesLevelsStatsAndItems() throws Exception {
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

            w.s.tx.inTx(() -> w.s.progress.addCounter(p, "talk.npc", 2000));   // 매력 스탯의 바탕 기록
            long charmBefore = w.s.growth.statPoints(p, "charm");
            w.now.addAndGet(8L * 86_400_000L);
            var o = w.s.deaths.die(p, "fallen_crater", 4, List.of());
            assertFalse(o.beginner());
            assertEquals(5, w.s.growth.level(p, "mining"), "원작식: 진행도가 모자라면 레벨이 떨어진다");
            assertTrue(o.penalty().drops() >= 1, "무작위 아이템 드롭");
            assertEquals(2000 - 120, w.s.growth.counter(p, "talk.npc"), "원작식: 스탯 바탕 기록이 (2 + 위험도)% 준다");
            assertTrue(w.s.growth.statPoints(p, "charm") < charmBefore, "스탯이 떨어진다");
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
            assertTrue(DeathPenalty.computeCanon(m, Map.of(), 2, 2).totalXpLoss() > DeathPenalty.computeCanon(m, Map.of(), 2, 1).totalXpLoss());
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

    @Test
    void capsuleSubscriptionAndMinorRules() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertNull(w.s.access.admit(p), "기본은 모두 꺼짐");
            w.s.access.rules(new AccessService.Rules(true, 1000, 30));
            String q = TestWorld.player();
            assertNotNull(w.s.access.admit(q), "캡슐이 없으면 못 들어온다");
            w.s.access.registerCapsule(q);
            assertNull(w.s.access.admit(q), "첫 30일은 무료");
            long until = w.s.access.paidUntil(q);
            w.now.addAndGet(31L * 86_400_000L);
            assertNotNull(w.s.access.admit(q), "기간이 끝나고 돈이 없으면 못 들어온다");
            w.s.economy.deposit(q, 1500, "test", "q1");
            assertNull(w.s.access.admit(q), "돈이 있으면 자동 결제");
            assertEquals(500, w.s.economy.balance(q));
            assertTrue(w.s.access.paidUntil(q) > until + 30L * 86_400_000L);
            assertNull(w.s.access.admit(q), "같은 기간에 두 번 내지 않는다");
            assertEquals(500, w.s.economy.balance(q));
            // 미성년: 던전에 못 들어간다
            w.s.access.setMinor(q, true);
            assertTrue(w.s.access.minor(q));
            String dungeon = w.s.content.dungeons().get(0).id();
            int min = w.s.content.dungeons().get(0).minParty();
            java.util.List<String> party = new java.util.ArrayList<>(List.of(q));
            while (party.size() < min) party.add(TestWorld.player());
            assertThrows(DomainException.class, () -> w.s.dungeons.start(dungeon, party, 1));
            w.s.access.setMinor(q, false);
            assertFalse(w.s.access.minor(q));
        }
    }

    private static String relic(TestWorld w, String owner, String type) {
        var it = w.s.items.create(type, 700, null, "test", "test", Map.of(), owner, "relic:" + owner + ":" + type);
        w.s.items.confirmDelivered(it.id(), owner);
        return it.id();
    }

    @Test
    void secretArtsNeedJobMasteryAndRelicOrInsightThenTheFinalArt() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertThrows(DomainException.class, () -> w.s.arts.learn(p, "sculpt_life", null), "조각가가 아니면 못 배운다");
            w.s.tx.inTx(() -> {
                w.s.progress.setMasteryXp(p, "sculpting", Mastery.cumulative(25));
                w.s.progress.addCounter(p, "art.experience", 100);
                return null;
            });
            w.s.jobs.advance(p, "sculptor", w.s.facts(p, null, 12));
            var e = assertThrows(DomainException.class, () -> w.s.arts.learn(p, "sculpt_life", null));
            assertTrue(e.getMessage().contains("조각상"), "조각상도 깨우침도 없으면 못 배운다");
            String statue = relic(w, p, "relic_life_statue");
            assertEquals("relic", w.s.arts.learn(p, "sculpt_life", statue));
            assertFalse(w.s.items.find(statue).orElseThrow().custody().ownedBy(p), "바친 조각상은 사라진다");
            assertThrows(DomainException.class, () -> w.s.arts.learn(p, "sculpt_life", null), "두 번 배우지 않는다");
            assertThrows(DomainException.class, () -> w.s.arts.learn(p, "time_sculpting", null), "다른 비기를 다 익혀야 최후의 비기");
            w.s.tx.inTx(() -> {
                w.s.progress.addCounter(p, "art.experience", 2000);
                w.s.progress.addCounter(p, "craft.sculpting", 3000);
                w.s.progress.setMasteryXp(p, "exploration", Mastery.cumulative(15));
                w.s.progress.setMasteryXp(p, "sculpting", Mastery.cumulative(29));
                return null;
            });
            assertEquals("discover", w.s.arts.learn(p, "sculpt_transform", null), "스스로 깨우친다");
            assertEquals("discover", w.s.arts.learn(p, "spirit_creation", null), "정령창조 조각술은 스스로");
            assertEquals("relic", w.s.arts.learn(p, "sculpt_revival", relic(w, p, "relic_revival_statue")));
            assertEquals("final", w.s.arts.learn(p, "time_sculpting", null));
            assertEquals(1, io.versaera.domain.art.SecretArt.timeTier(w.s.arts.castLevel(p, "time_sculpting")), "숙련 29 = 초급 시간 가속");
            assertEquals(3, io.versaera.domain.art.SecretArt.timeTier(31));
            assertThrows(DomainException.class, () -> w.s.arts.castLevel(p, "heavenly_taste"), "익히지 않은 비기");
            // 천상의 맛: 하루 한 번 영구 인내 기록
            assertTrue(w.s.arts.feast(p));
            assertFalse(w.s.arts.feast(p));
            assertEquals(200, w.s.growth.counter(p, "hit_taken"));
        }
    }

    @Test
    void ironMenTrialRewardsOnce() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            w.s.trials.checkStart(p);
            assertTrue(w.s.trials.complete(p));
            assertFalse(w.s.trials.complete(p), "보상은 처음 한 번");
            assertTrue(w.s.trials.cleared(p));
            assertEquals(300, w.s.reputation.standing(p).fame());
            assertEquals(300, w.s.growth.counter(p, "hit.training"));
            w.s.access.setMinor(p, true);
            assertThrows(DomainException.class, () -> w.s.trials.checkStart(p));
        }
    }
}
