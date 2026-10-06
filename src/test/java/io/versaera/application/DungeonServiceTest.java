package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.dungeon.DungeonLayout.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DungeonServiceTest {
    private static void clearPath(TestWorld w, DungeonService.Handle h) {
        var l = h.run().layout();
        var path = l.mainPath();
        for (int id : path.subList(1, path.size() - 1)) {
            if (l.rooms().get(id).kind() == Kind.PUZZLE)
                for (int i = 0; i < h.run().puzzle().size(); i++) w.s.dungeons.pull(h.runId(), h.run().puzzle().clue(i));
            w.s.dungeons.roomCleared(h.runId(), id);
        }
    }

    @Test
    void clearPaysEachMemberOnceThenCooldown() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            assertEquals("dungeon.party", assertThrows(DomainException.class,
                    () -> w.s.dungeons.start("calamor_vault", List.of(a), 1)).code(), "최소 인원");
            var h = w.s.dungeons.start("aqueduct_cistern", List.of(a, b), 99);
            assertThrows(DomainException.class, () -> w.s.dungeons.start("aqueduct_cistern", List.of(a), 3), "동시에 두 던전 불가");
            clearPath(w, h);
            assertEquals(2, w.s.dungeons.bossDefeated(h.runId(), Map.of()).size());
            assertEquals(800, w.s.economy.balance(a));
            assertEquals(800, w.s.economy.balance(b));
            assertThrows(DomainException.class, () -> w.s.dungeons.bossDefeated(h.runId(), Map.of()), "끝난 판은 다시 보상 없음");
            assertTrue(w.s.dungeons.cooldownLeft(a, "aqueduct_cistern") > 0);

            var again = w.s.dungeons.start("aqueduct_cistern", List.of(a), 100);
            clearPath(w, again);
            assertTrue(w.s.dungeons.bossDefeated(again.runId(), Map.of()).isEmpty(), "재사용 대기 중에는 보상 없음");
            assertEquals(800, w.s.economy.balance(a));
            w.now.addAndGet(w.s.dungeons.dungeon("aqueduct_cistern").cooldownMs());
            var third = w.s.dungeons.start("aqueduct_cistern", List.of(a), 101);
            clearPath(w, third);
            int hiddenFrom = third.run().layout().neighbours(third.run().layout().room(Kind.HIDDEN).id(), true).get(0);
            if (third.run().isCleared(hiddenFrom)) assertTrue(w.s.dungeons.revealHidden(third.runId(), hiddenFrom));
            w.s.dungeons.bossDefeated(third.runId(), Map.of());
            assertTrue(w.s.economy.balance(a) >= 1600);
        }
    }

    @Test
    void restartFailsActiveRuns() throws Exception {
        try (TestWorld w = new TestWorld()) {
            w.s.dungeons.start("aqueduct_cistern", List.of(TestWorld.player()), 5);
            assertEquals(1, w.s.dungeons.recover());
        }
    }
}
