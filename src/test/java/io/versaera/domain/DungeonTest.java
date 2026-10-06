package io.versaera.domain;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.dungeon.DungeonLayout;
import io.versaera.domain.dungeon.DungeonLayout.Kind;
import io.versaera.domain.dungeon.DungeonRun;
import io.versaera.domain.dungeon.LeverPuzzle;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DungeonTest {
    @Test
    void layoutsAreConnectedDeterministicAndVaried() {
        Set<String> shapes = new HashSet<>();
        for (long seed = 0; seed < 300; seed++) {
            for (int rooms : new int[]{5, 9, 15, 40}) {
                DungeonLayout l = DungeonLayout.generate(seed, rooms);
                assertEquals(rooms, l.rooms().size());
                int[] open = l.distances(false), all = l.distances(true);
                int hidden = l.room(Kind.HIDDEN).id();
                for (var r : l.rooms()) {
                    assertTrue(all[r.id()] >= 0, "모든 방은 이어져 있다 seed=" + seed);
                    if (r.id() != hidden) assertTrue(open[r.id()] >= 0, "숨은 방 말고는 일반 문으로 갈 수 있다");
                }
                assertEquals(-1, open[hidden], "숨은 방은 숨은 벽으로만");
                int boss = l.room(Kind.BOSS).id();
                for (var r : l.rooms()) if (r.id() != hidden) assertTrue(open[r.id()] <= open[boss], "보스는 가장 먼 방");
                assertTrue(l.mainPath().contains(l.room(Kind.PUZZLE).id()), "퍼즐은 보스로 가는 길 위");
                Set<Long> cells = new HashSet<>();
                for (var r : l.rooms()) assertTrue(cells.add(((long) r.gx() << 32) ^ (r.gz() & 0xffffffffL)), "방이 겹치지 않는다");
                assertEquals(l.rooms(), DungeonLayout.generate(seed, rooms).rooms(), "같은 시드 = 같은 배치");
                if (rooms == 15) shapes.add(l.rooms().toString());
            }
        }
        assertTrue(shapes.size() > 250, "시드마다 다른 배치");
    }

    @Test
    void puzzleIsSolvedByObservationNotLuck() {
        LeverPuzzle p = new LeverPuzzle(42, 5);
        assertEquals(LeverPuzzle.Result.WRONG, p.pull((p.clue(0) + 1) % 5));
        for (int i = 0; i < 4; i++) assertEquals(LeverPuzzle.Result.PROGRESS, p.pull(p.clue(i)));
        assertEquals(LeverPuzzle.Result.SOLVED, p.pull(p.clue(4)));
        assertTrue(p.solved());
    }

    @Test
    void bossDoorOpensOnlyAfterPathAndPuzzle() {
        DungeonLayout l = DungeonLayout.generate(7, 9);
        DungeonRun run = new DungeonRun(l, List.of("a", "b"), 0, 60_000, 4);
        int boss = l.room(Kind.BOSS).id();
        assertFalse(run.canEnter(boss));
        assertThrows(DomainException.class, () -> run.bossDefeated(10));
        List<Integer> path = l.mainPath();
        for (int id : path.subList(1, path.size() - 1)) {
            if (l.rooms().get(id).kind() == Kind.PUZZLE) {
                assertEquals("dungeon.puzzle", assertThrows(DomainException.class, () -> run.roomCleared(id, 10)).code());
                for (int i = 0; i < run.puzzle().size(); i++) run.pull(run.puzzle().clue(i), 10);
            }
            run.roomCleared(id, 10);
        }
        assertTrue(run.bossUnlocked());
        run.bossDefeated(20);
        assertEquals(DungeonRun.State.CLEARED, run.state());
    }

    @Test
    void wipeOrTimeoutFails() {
        DungeonRun run = new DungeonRun(DungeonLayout.generate(1, 7), List.of("a", "b"), 0, 1000, 3);
        run.memberDown("a", 10);
        assertEquals(DungeonRun.State.ACTIVE, run.state());
        run.memberDown("b", 20);
        assertEquals(DungeonRun.State.FAILED, run.state());
        DungeonRun slow = new DungeonRun(DungeonLayout.generate(1, 7), List.of("a"), 0, 1000, 3);
        slow.tick(1001);
        assertEquals(DungeonRun.State.FAILED, slow.state());
    }
}
