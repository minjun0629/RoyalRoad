package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.world.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrowthAndWorldTest {
    @Test
    void actionStatAppearsOnlyAfterUnlockAndGrows() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            for (int i = 0; i < 4; i++) w.s.growth.record(p, "discover.landmark", 1);
            assertFalse(w.s.growth.statPoints(p).containsKey("insight"), "통찰은 5번 발견해야 생긴다");
            w.s.growth.record(p, "discover.landmark", 1);
            assertTrue(w.s.growth.statPoints(p).containsKey("insight"));
            assertTrue(w.eventsOf(GameEvents.StatGained.class).stream().anyMatch(e -> e.statId().equals("insight") && e.newlyUnlocked()));
        }
    }

    @Test
    void handDisciplinesBenefitFromDexterity() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            w.s.progress.setMasteryXp(b, "dexterity", io.versaera.domain.skill.Mastery.cumulative(20));
            long ga = w.s.growth.addXp(a, "smithing", 100, 1).gained();
            long gb = w.s.growth.addXp(b, "smithing", 100, 1).gained();
            assertTrue(gb > ga, "손재주가 높으면 손을 쓰는 숙련이 빨리 오른다");
        }
    }

    @Test
    void regionsResolveMostSpecificAndUndergroundSeparately() throws Exception {
        try (TestWorld w = new TestWorld()) {
            assertEquals("harden", w.s.regions.at("world", -1000, 70, 0).id());
            assertEquals("emperor_aqueduct", w.s.regions.at("world", -1000, 0, 0).id(), "도시 아래 지하는 수로");
            assertEquals("central_plains", w.s.regions.at("world", -3200, 70, 2000).id());
            assertEquals("aren_castle", w.s.regions.at("world", -1500, 70, -1100).id(), "하벤의 수도 아렌 성");
            assertEquals("sisley_castle", w.s.regions.at("world", 2400, 70, -150).id(), "브리튼 연합의 시슬레 성");
            assertEquals("yunopu_canyon", w.s.regions.at("world", 5100, 70, -1200).id(), "절망의 평원 북쪽 협곡");
            assertEquals("jigolas", w.s.regions.at("world", 1300, 70, -5600).id());
            assertEquals("rosenheim", w.s.regions.at("world", 4700, 70, 2200).id());
            assertEquals("lavias", w.s.regions.at("world", 1700, 210, -4300).id(), "천공도시는 북부 하늘");
            assertEquals("serabourg", w.s.regions.at("world", 4200, 70, 1700).id());
            assertEquals("nehales_bastion", w.s.regions.at("world", 4100, 80, -700).id());
            assertEquals("embinyu_sanctum", w.s.regions.at("world", -5300, 70, -2000).id(), "서부 폐허의 거대한 구멍");
            assertEquals("metapeia", w.s.regions.at("world", 3500, 70, 5500).id());
            assertEquals("roderick_labyrinth", w.s.regions.at("world", 950, 70, -2150).id(), "8대 미궁 로드릭");
            assertEquals("wolhof_coral", w.s.regions.at("world", 5900, 50, 0).id(), "9대 비경 울호프 산호지대");
            assertNull(w.s.regions.at("world_nether", 0, 70, 0));
            for (Region r : w.s.regions.all()) {
                assertFalse(r.purpose().isBlank(), r.id());
                if ("CANON".equals(r.source())) assertNotNull(r.changed(), "원작 지명은 후대 변화를 적어야 함: " + r.id());
            }
        }
    }

    @Test
    void firstDiscovererIsRecordedOnce() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            Region crater = w.s.regions.byId("fallen_crater");
            assertTrue(w.s.exploration.enterRegion(a, "A", crater).worldFirst());
            var second = w.s.exploration.enterRegion(b, "B", crater);
            assertTrue(second.isNew());
            assertFalse(second.worldFirst());
            assertFalse(w.s.exploration.enterRegion(a, "A", crater).isNew(), "다시 들어가도 새 발견이 아님");
            assertEquals("A", w.s.exploration.worldFirst("region", "fallen_crater").orElseThrow().name());
            assertTrue(w.s.growth.xp(a, "exploration") > 0);
        }
    }

    @Test
    void talkingRaisesAffinityOncePerDay() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertEquals(5, w.s.relations.talk(p, "oren_smith"));
            assertEquals(0, w.s.relations.talk(p, "oren_smith"), "같은 날 반복 대화는 의미 없음");
            w.now.addAndGet(24L * 3600 * 1000);
            assertEquals(5, w.s.relations.talk(p, "oren_smith"));
            assertEquals(10, w.s.relations.affinity(p, "oren_smith"));
            assertTrue(w.s.relations.gift(p, "oren_smith", java.util.Set.of("metal"), 1000) > 30);
            assertTrue(w.s.relations.gift(p, "oren_smith", java.util.Set.of("potion"), 1000) < 0);
        }
    }
}
