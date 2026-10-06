package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.crafting.MaterialInput;
import io.versaera.domain.quest.QuestDefinition.Type;
import io.versaera.domain.skill.Mastery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JobQuestDeathTest {
    private static void xp(TestWorld w, String p, String d, int level) {
        w.s.tx.inTx(() -> {
            w.s.progress.setMasteryXp(p, d, Mastery.cumulative(level));
            return null;
        });
    }

    private static MaterialInput mat(String type, int q, int n) {
        return new MaterialInput(type, Set.of(), q, n);
    }

    @Test
    void jobsOpenFromMasteryAndDeedsNotLevels() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertEquals("job.requirements", assertThrows(DomainException.class,
                    () -> w.s.jobs.advance(p, "swordsman", w.s.facts(p, null, 12))).code());
            xp(w, p, "swordsmanship", 5);
            w.s.growth.record(p, "kill.monster", 30);
            assertTrue(w.s.jobs.available(p, w.s.facts(p, null, 12)).stream().anyMatch(j -> j.id().equals("swordsman")));
            w.s.jobs.advance(p, "swordsman", w.s.facts(p, null, 12));
            assertEquals("swordsman", w.s.jobs.held(p).get("COMBAT").jobId());
            assertTrue(w.s.jobs.skills(p).contains("slash_arc"));
            assertEquals(0.05, w.s.jobs.perks(p).get("attack_pct"), 1e-9);
            // 다른 계열로 바꾸기는 7일 뒤
            xp(w, p, "archery", 5);
            assertEquals("job.cooldown", assertThrows(DomainException.class, () -> w.s.jobs.advance(p, "archer", w.s.facts(p, null, 12))).code());
            w.now.addAndGet(JobService.CHANGE_COOLDOWN_MS + 1);
            w.s.jobs.advance(p, "archer", w.s.facts(p, null, 12));
            assertEquals("archer", w.s.jobs.held(p).get("COMBAT").jobId());
            // 상위 직업은 하위 직업이 있어야
            assertEquals("job.need_parent", assertThrows(DomainException.class, () -> w.s.jobs.advance(p, "knight", w.s.facts(p, null, 12))).code());
        }
    }

    @Test
    void lifeJobPerksRaiseCraftQualityDeterministically() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            for (String p : List.of(a, b)) xp(w, p, "sculpting", 10);
            w.s.growth.record(b, "art.experience", 80);
            w.s.jobs.advance(b, "sculptor", w.s.facts(b, null, 12));
            var mats = List.of(new MaterialInput("oak_timber", Set.of("wood"), 500, 2), new MaterialInput("iron_ingot", Set.of("metal", "mineral"), 500, 1));
            int qa = w.s.crafting.craft(a, "a", "carve_figurine", mats, 500, null, new java.util.SplittableRandom(3), null).quality();
            int qb = w.s.crafting.craft(b, "b", "carve_figurine", mats, 500, null, new java.util.SplittableRandom(3), null).quality();
            assertTrue(qb > qa, "같은 재료 · 같은 운에서 조각가가 더 잘 만든다: " + qa + " vs " + qb);
        }
    }

    @Test
    void deathCostsProgressNotLevels() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            long base = Mastery.cumulative(6), xp = base + Mastery.need(6) / 2;
            w.s.tx.inTx(() -> {
                w.s.progress.setMasteryXp(p, "mining", xp);
                return null;
            });
            var r = w.s.deaths.die(p, "fallen_crater", 4, List.of());
            assertTrue(r.heavyWear());
            long after = w.s.growth.xp(p, "mining");
            assertTrue(after < xp && after >= base, "진행도만 줄고 레벨은 그대로: " + after);
            assertEquals(6, w.s.growth.level(p, "mining"));
            assertEquals(1, w.s.growth.counter(p, "death"));
        }
    }

    @Test
    void questRewardPaidOnceAndDeliveriesChecked() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            var f = w.s.facts(p, null, 12);
            assertTrue(w.s.quests.available(p, "kael_trader", f).stream().anyMatch(q -> q.id().equals("harden.first_coin")));
            w.s.quests.accept(p, "harden.first_coin", f);
            assertEquals("quest.cannot_accept", assertThrows(DomainException.class, () -> w.s.quests.accept(p, "harden.first_coin", f)).code());
            assertEquals("quest.not_done", assertThrows(DomainException.class,
                    () -> w.s.quests.complete(p, "P", "harden.first_coin", null, List.of(mat("oak_timber", 500, 6)))).code());
            assertEquals(6, w.s.items.pendingBulk(p).get(0).amount(), "실패하면 납품한 재료는 돌려준다");
            w.s.quests.record(p, Type.GATHER, "oak_timber", 6, 500);
            assertEquals("quest.missing_delivery", assertThrows(DomainException.class,
                    () -> w.s.quests.complete(p, "P", "harden.first_coin", null, List.of(mat("oak_timber", 500, 3)))).code());
            w.s.quests.complete(p, "P", "harden.first_coin", null, List.of(mat("oak_timber", 500, 6)));
            assertEquals(300, w.s.economy.balance(p));
            assertEquals(10, w.s.quests.reputations(p).get("harden_merchants"));
            assertThrows(DomainException.class, () -> w.s.quests.complete(p, "P", "harden.first_coin", null, List.of()));
            assertEquals(300, w.s.economy.balance(p), "보상은 한 번만");
            assertThrows(DomainException.class, () -> w.s.quests.accept(p, "harden.first_coin", f), "일일 퀘스트가 아니면 다시 못 받음");
        }
    }

    @Test
    void choicesChangeRewardsAndFollowUps() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            var f = w.s.facts(p, null, 12);
            assertThrows(DomainException.class, () -> w.s.quests.accept(p, "harden.price_of_salt", f), "선행 퀘스트 필요");
            w.s.quests.accept(p, "harden.first_coin", f);
            w.s.quests.record(p, Type.GATHER, "oak_timber", 6, 500);
            w.s.quests.complete(p, "P", "harden.first_coin", null, List.of(mat("oak_timber", 500, 6)));
            w.s.exploration.discover(p, "P", "region", "rosaim_harbor");
            w.s.quests.accept(p, "harden.price_of_salt", f);
            w.s.quests.record(p, Type.TALK, "tobi_fisher", 1, 0);
            assertEquals("quest.need_choice", assertThrows(DomainException.class,
                    () -> w.s.quests.complete(p, "P", "harden.price_of_salt", null, List.of(mat("sea_salt", 400, 10)))).code());
            w.s.quests.complete(p, "P", "harden.price_of_salt", "corner_market", List.of(mat("sea_salt", 400, 10)));
            assertEquals(300 + 900 + 1200, w.s.economy.balance(p));
            assertEquals(-5, w.s.quests.reputations(p).get("rosaim_ports"), "15 - 20");
        }
    }

    @Test
    void dailyQuestResetsNextDayAndAbandonAllowsRetry() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            var f = w.s.facts(p, null, 12);
            w.s.quests.accept(p, "harden.tavern_menu", f);
            w.s.quests.record(p, Type.CRAFT, "cook_stew", 1, 200);
            assertEquals(0, w.s.quests.active(p).get(0).progress().get(0), "품질이 모자라면 세지 않음");
            w.s.quests.record(p, Type.CRAFT, "cook_stew", 2, 400);
            w.s.quests.complete(p, "P", "harden.tavern_menu", null, List.of());
            assertThrows(DomainException.class, () -> w.s.quests.accept(p, "harden.tavern_menu", f));
            w.now.addAndGet(24L * 3600 * 1000);
            w.s.quests.accept(p, "harden.tavern_menu", f);
            w.s.quests.abandon(p, "harden.tavern_menu");

            w.s.quests.accept(p, "harden.drill", f);
            w.s.quests.abandon(p, "harden.drill");
            w.s.quests.accept(p, "harden.drill", f);
        }
    }

    @Test
    void hiddenQuestsStayInvisibleUntilUnlocked() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            var f = w.s.facts(p, null, 12);
            assertTrue(w.s.quests.available(p, null, f).stream().noneMatch(q -> q.id().equals("hidden.wind_song")));
            w.s.exploration.discover(p, "P", "quest", "hidden.wind_song");
            assertTrue(w.s.quests.available(p, null, f).stream().anyMatch(q -> q.id().equals("hidden.wind_song")));
        }
    }
}
