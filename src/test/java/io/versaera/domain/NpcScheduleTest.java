package io.versaera.domain;

import io.versaera.domain.npc.NpcDefinition;
import io.versaera.domain.npc.NpcSchedule;
import io.versaera.domain.npc.NpcSchedule.Point;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NpcScheduleTest {
    private final NpcDefinition smith = new NpcDefinition("s", "S", "smith", "", null, "r", Set.of(), Set.of(),
            List.of("6-12:forge", "12-13:tavern", "13-20:forge", "20-6:home"), "ORIGINAL");
    private final Map<String, Point> places = Map.of("forge", new Point(0, 0), "tavern", new Point(30, 40), "home", new Point(-10, 0));

    @Test
    void followsTheDayIncludingOvernightRanges() {
        assertEquals(places.get("forge"), NpcSchedule.target(smith, places, 8));
        assertEquals(places.get("tavern"), NpcSchedule.target(smith, places, 12));
        assertEquals(places.get("home"), NpcSchedule.target(smith, places, 23));
        assertEquals(places.get("home"), NpcSchedule.target(smith, places, 3));
    }

    @Test
    void walksAtLimitedSpeedAndArrives() {
        Point a = new Point(0, 0), b = new Point(30, 40);
        Point p = NpcSchedule.step(a, b, 1);
        assertEquals(NpcSchedule.WALK_PER_SECOND, a.dist(p), 1e-9);
        for (int i = 0; i < 20; i++) p = NpcSchedule.step(p, b, 1);
        assertEquals(b, p);
    }

    @Test
    void onlySimulatesNearPlayers() {
        assertFalse(NpcSchedule.active(new Point(0, 0), List.of(new Point(500, 0))));
        assertTrue(NpcSchedule.active(new Point(0, 0), List.of(new Point(500, 0), new Point(10, 10))));
    }
}
