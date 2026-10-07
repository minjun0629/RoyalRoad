package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.item.ItemOptions;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.skill.LifeSkills;
import io.versaera.domain.skill.Mastery;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class GearAndFieldBossTest {
    private static final RandomGenerator ALWAYS = () -> 0L;          // nextDouble() == 0 → 모든 확률이 성공
    private static final RandomGenerator NEVER = () -> -1L;          // nextDouble() ≈ 1 → 모든 확률이 실패

    private static String give(TestWorld w, String owner, String type) {
        var it = w.s.items.create(type, 600, null, "test", "test", Map.of(), owner, "give:" + owner + ":" + type + ":" + UUID.randomUUID());
        w.s.items.confirmDelivered(it.id(), owner);
        return it.id();
    }

    @Test
    void itemOptionsElementsSlayersAndSets() throws Exception {
        try (TestWorld w = new TestWorld()) {
            var types = w.s.items.types();
            ItemType holy = types.get("agatha_holy_sword");
            var vsUndead = ItemOptions.onHit(holy.stats(), 1.0, Set.of(ItemOptions.Kind.UNDEAD));
            var vsBeast = ItemOptions.onHit(holy.stats(), 1.0, Set.of());
            assertEquals(12, vsUndead.bonus(), 1e-9, "신성 피해는 언데드에게 두 배");
            assertEquals(6, vsBeast.bonus(), 1e-9);
            assertEquals(1.3, vsUndead.mult(), 1e-9, "언데드 추가 피해 30%");
            assertEquals(List.of("holy"), vsUndead.elements());
            assertTrue(ItemOptions.onHit(types.get("coldrim_demon_sword").stats(), 1, Set.of()).lifesteal() > 0);

            List<ItemType> graham = List.of(types.get("graham_helm"), types.get("graham_plate"), types.get("graham_greaves"), types.get("graham_boots"));
            Map<String, Integer> all = ItemOptions.total(graham, types.sets());
            assertEquals(7 + 14 + 10 + 6 + 6, all.get("defense"), "4벌: 2벌 보너스도 함께");
            assertEquals(10, all.get("resist"));
            Map<String, Integer> two = ItemOptions.total(graham.subList(0, 2), types.sets());
            assertNull(two.get("resist"), "2벌은 4벌 보너스가 없다");
            var passive = ItemOptions.passive(Map.of("resist", 90, "drain", 1, "speed", -50));
            assertEquals(40, passive.resistPct(), 1e-9, "피해 감소는 40% 까지");
            assertEquals(-30, passive.speedPct(), 1e-9);
            assertEquals(1, passive.drain(), 1e-9);
            assertThrows(DomainException.class, () -> new ItemType("x", "x", io.versaera.domain.item.ItemCategory.WEAPON, "STICK", 1, 1,
                    Set.of(), Map.of("laser", 1), Map.of(), "ORIGINAL"), "없는 능력은 콘텐츠 오류");
        }
    }

    @Test
    void requirementsShrinkWithSmithingAndAppraisalRevealsOnce() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            ItemType roa = w.s.items.types().get("roa_masterpiece");   // 검술 18
            w.s.tx.inTx(() -> { w.s.progress.setMasteryXp(p, "swordsmanship", Mastery.cumulative(13)); return null; });
            assertEquals(Map.of("mastery.swordsmanship", 18L), GearService.unmet(roa, w.s.gear.context(p)));
            w.s.tx.inTx(() -> { w.s.progress.setMasteryXp(p, "smithing", Mastery.cumulative(31)); return null; });
            assertTrue(GearService.unmet(roa, w.s.gear.context(p)).isEmpty(), "대장 기술 31 → 조건 31% 감소 (18 → 13)");
            ItemType agatha = w.s.items.types().get("agatha_holy_sword");
            assertTrue(GearService.unmet(agatha, w.s.gear.context(p)).containsKey("fame"), "명성 조건");

            String sword = give(w, p, "calamor_sword");
            var fail = w.s.gear.appraise(p, sword, NEVER);
            assertFalse(fail.success(), "감정 숙련 1 로는 어렵다");
            var ok = w.s.gear.appraise(p, sword, ALWAYS);
            assertTrue(ok.success() && ok.firstOfKind());
            assertTrue(GearService.appraised(w.s.items.find(sword).orElseThrow()), "감정 기록은 아이템에 남는다");
            assertThrows(DomainException.class, () -> w.s.gear.appraise(p, sword, ALWAYS), "두 번 감정하지 않는다");
            assertThrows(DomainException.class, () -> w.s.gear.appraise(p, give(w, p, "iron_dagger"), ALWAYS), "숨은 것 없는 물건");
            assertThrows(DomainException.class, () -> w.s.gear.appraise(TestWorld.player(), sword, ALWAYS), "남의 물건");
            assertTrue(w.s.growth.xp(p, "appraisal") > 0);
        }
    }

    @Test
    void fieldBossRewardsOncePerSpawnAndRespawnsLater() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player(), c = TestWorld.player();
            assertTrue(w.s.fieldBosses.all().size() >= 10);
            assertTrue(w.s.fieldBosses.ready("van_hawk"));
            long gen = w.s.fieldBosses.generation("van_hawk");
            var d = w.s.fieldBosses.defeated("van_hawk", gen, Map.of(a, 800.0, b, 150.0, c, 50.0), Map.of(a, "A", b, "B", c, "C"), ALWAYS);
            assertEquals(a, d.top());
            assertEquals(Set.of(a, b), d.rewarded(), "기여 10% 미만은 보상 없음");
            assertTrue(d.drops().contains("반 호크의 투구"), "확률 드롭은 가장 많이 때린 사람에게");
            assertTrue(d.worldFirst());
            assertEquals(4000, w.s.economy.balance(a));
            assertEquals(4000, w.s.economy.balance(b));
            assertEquals(0, w.s.economy.balance(c));
            assertEquals(30, w.s.reputation.standing(a).fame());
            assertTrue(w.s.items.pendingDeliveries(a).stream().anyMatch(it -> it.typeId().equals("van_hawk_helm")));
            assertThrows(DomainException.class, () -> w.s.fieldBosses.defeated("van_hawk", gen, Map.of(a, 1.0), Map.of(), ALWAYS),
                    "같은 출현은 한 번만 보상");
            assertFalse(w.s.fieldBosses.ready("van_hawk"), "쓰러지면 한동안 안 나온다");
            w.now.addAndGet(121 * 60_000L);
            assertTrue(w.s.fieldBosses.ready("van_hawk"));
            var none = w.s.fieldBosses.defeated("van_hawk", gen + 1, Map.of(a, 1.0), Map.of(), NEVER);
            assertTrue(none.drops().isEmpty());
            assertFalse(none.worldFirst());
        }
    }

    @Test
    void lifeSkillsCareButcherBandageAndDestruction() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            String sword = give(w, p, "iron_longsword"), jerkin = give(w, p, "leather_jerkin"), mail = give(w, p, "iron_mail");
            assertEquals("whetting", w.s.life.care(p, "whetting", sword).discipline());
            assertThrows(DomainException.class, () -> w.s.life.care(p, "whetting", jerkin));
            assertThrows(DomainException.class, () -> w.s.life.care(p, "polishing", jerkin), "가죽은 다림질");
            assertTrue(w.s.life.care(p, "ironing", jerkin).pct() >= 5);
            assertTrue(w.s.life.care(p, "polishing", mail).pct() >= 5);
            assertTrue(w.s.growth.xp(p, "polishing") > 0);

            assertEquals(LifeSkills.butcherExtra(1), w.s.life.butcher(p, true, ALWAYS));
            assertEquals(0, w.s.life.butcher(p, false, NEVER));
            assertEquals(1, w.s.items.pendingBulk(p).size(), "도축으로 더 얻은 가죽은 배달함으로");
            assertTrue(w.s.life.bandage(p, false) > w.s.life.bandage(p, true), "전투 중엔 덜 낫는다");
            assertThrows(DomainException.class, () -> w.s.life.roar(p), "사자후는 검술 5 부터");

            String statue = give(w, p, "figurine");
            assertThrows(DomainException.class, () -> w.s.life.destroySculpture(p, statue), "조각가만");
            w.s.tx.inTx(() -> { w.s.progress.setMasteryXp(p, "sculpting", Mastery.cumulative(10)); w.s.progress.addCounter(p, "art.experience", 100); return null; });
            w.s.jobs.advance(p, "sculptor", w.s.facts(p, null, 12));
            var r = w.s.life.destroySculpture(p, statue);
            assertTrue(r.minutes() >= 3);
            assertFalse(w.s.items.find(statue).orElseThrow().custody().ownedBy(p), "부순 조각품은 사라진다");
            assertEquals(1.3, LifeSkills.focusMult(6), 1e-9);
            assertEquals(1.3, LifeSkills.focusMult(99), 1e-9, "일점 공격은 6번까지만 쌓인다");
        }
    }
}
