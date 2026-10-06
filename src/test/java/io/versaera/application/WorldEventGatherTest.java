package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.worldevent.WorldEventClock;
import io.versaera.domain.worldevent.WorldEventDefinition;
import io.versaera.domain.skill.Mastery;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorldEventGatherTest {
    private static final WorldEventDefinition STORM = new WorldEventDefinition("storm", "s", "sand_sea", WorldEventDefinition.Kind.SANDSTORM,
            30 * 3_600_000L, 50 * 60_000L, 8 * 3_600_000L, 3 * 3_600_000L, "dariq_caravan", Map.of("reveal", "region:buried_city"), "", "ORIGINAL");

    @Test
    void scheduleIsDeterministicNonOverlappingAndForecastable() {
        WorldEventClock a = new WorldEventClock(1234, 0), b = new WorldEventClock(1234, 0), other = new WorldEventClock(999, 0);
        int diff = 0;
        for (long n = 0; n < 200; n++) {
            var w = a.window(STORM, n);
            assertEquals(w, b.window(STORM, n), "같은 시드 = 같은 시간표");
            assertTrue(w.end() <= a.window(STORM, n + 1).start(), "겹치지 않는다");
            assertNotNull(a.activeAt(STORM, w.start()));
            assertNull(a.activeAt(STORM, w.end()));
            assertEquals(w.start(), a.forecast(STORM, w.start() - 3_600_000L), "시작 한 시간 전엔 예보된다");
            if (!w.equals(other.window(STORM, n))) diff++;
        }
        assertTrue(diff > 150, "다른 서버(시드)는 다른 시간표");
        var w = a.window(STORM, 5);
        assertEquals(-1, a.forecast(STORM, w.start() - 4 * 3_600_000L - 1), "예보 시간 밖이면 모른다");
    }

    @Test
    void eventsStartAndEndOnceAndApplyRegionally() throws Exception {
        try (TestWorld w = new TestWorld()) {
            var ev = w.s.worldEvents;
            var vein = ev.all().stream().filter(d -> d.id().equals("silver_seam")).findFirst().orElseThrow();
            var win = ev.clock().next(vein, w.now.get());
            w.now.set(win.start() + 1000);
            assertTrue(ev.tick().stream().anyMatch(c -> c.def().id().equals("silver_seam") && c.started()));
            assertTrue(ev.tick().stream().noneMatch(c -> c.def().id().equals("silver_seam")), "같은 변화는 한 번만 알린다");
            assertEquals(2, ev.gatherBonus("thor_deep_hammer", "mining"));
            assertEquals(0, ev.gatherBonus("harden", "mining"), "다른 지역에는 효과 없음");

            String p = TestWorld.player();
            var node = w.s.gathering.all().stream().filter(n -> n.discipline().equals("mining") && n.minLevel() <= 1).findFirst().orElseThrow();
            var r = w.s.gathering.gather(p, node.id(), "thor_deep_hammer", Set.of("mountain"), 1);
            assertEquals(2, r.bonus());
            w.now.set(win.end() + 1000);
            assertTrue(ev.tick().stream().anyMatch(c -> c.def().id().equals("silver_seam") && !c.started()));
            assertEquals(0, w.s.gathering.gather(p, node.id(), "thor_deep_hammer", Set.of("mountain"), 1).bonus());
        }
    }

    @Test
    void lifeJobAddsGatherBonusAndQuestProgress() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            w.s.tx.inTx(() -> {
                w.s.progress.setMasteryXp(p, "mining", Mastery.cumulative(10));
                return null;
            });
            w.s.growth.record(p, "gather.mining", 300);
            w.s.jobs.advance(p, "miner", w.s.facts(p, null, 12));
            var node = w.s.gathering.all().stream().filter(n -> n.discipline().equals("mining") && n.minLevel() <= 1).findFirst().orElseThrow();
            assertEquals(1, w.s.gathering.gather(p, node.id(), "harden", Set.of(), 5).bonus());
            assertNull(w.s.gathering.gather(TestWorld.player(), w.s.gathering.all().stream().filter(n -> n.minLevel() > 1).findFirst().orElseThrow().id(),
                    "harden", Set.of(), 5), "숙련이 모자라면 못 캔다");
        }
    }
}
