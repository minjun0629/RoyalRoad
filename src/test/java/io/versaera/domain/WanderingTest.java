package io.versaera.domain;

import io.versaera.domain.npc.NpcSchedule.Point;
import io.versaera.domain.npc.Wandering;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WanderingTest {
    private static final List<Point> ROUTE = List.of(new Point(0, 0), new Point(1200, 0), new Point(1200, 1200));
    private static final long STAY = 10 * 60_000;

    @Test
    void staysThenWalksAtTheGivenSpeedAndLoopsBack() {
        Wandering.State s = Wandering.at(ROUTE, 120, STAY, 5 * 60_000, 0);
        assertEquals(0, s.stop());
        assertEquals(5 * 60_000, s.arriveInMs());
        s = Wandering.at(ROUTE, 120, STAY, STAY + 5 * 60_000, 0);   // 1200 블록 / 분당 120 = 10분 → 반쯤
        assertEquals(-1, s.stop());
        assertEquals(600, s.where().x(), 1e-6);
        assertEquals(1, s.next());
        s = Wandering.at(ROUTE, 120, STAY, STAY + 10 * 60_000 + 1, 0);
        assertEquals(1, s.stop());
        // 한 바퀴: 머묾 3번 + 1200 + 1200 + 1697 블록
        long loop = 3 * STAY + 10 * 60_000 * 2 + Math.round(Math.hypot(1200, 1200) / 120 * 60_000);
        assertEquals(Wandering.at(ROUTE, 120, STAY, 777_777, 0).where(), Wandering.at(ROUTE, 120, STAY, 777_777 + loop, 0).where());
    }

    @Test
    void offsetsSpreadWanderersAlongTheSameRoad() {
        assertNotEquals(Wandering.at(ROUTE, 120, STAY, 0, 0).where(), Wandering.at(ROUTE, 120, STAY, 0, 25 * 60_000).where());
    }
}
