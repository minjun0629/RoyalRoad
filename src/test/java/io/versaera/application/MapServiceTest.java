package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.map.FogMap;
import io.versaera.domain.map.FogMap.Tone;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MapServiceTest {
    @Test
    void onlyVisitedCellsAreVisibleAndPersisted() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertEquals(9, w.s.maps.visit(p, 0, 0));
            assertEquals(0, w.s.maps.visit(p, 5, 5), "같은 칸은 다시 쓰지 않는다");
            assertEquals(3, w.s.maps.visit(p, FogMap.CELL, 0), "옆 칸으로 가면 새 줄만");
            w.s.maps.forget(p);
            assertEquals(12, w.s.maps.explored(p).size(), "DB 에서 다시 읽음");

            var known = w.s.maps.snapshot(p);
            Tone[] px = FogMap.render(0, 0, 1, (cx, cz) -> known.contains(FogMap.pack(cx, cz)),
                    (x, z) -> w.s.regions.at("world", x, 64, z), 0, 0);
            assertEquals(FogMap.SIZE * FogMap.SIZE, px.length);
            assertEquals(Tone.YOU, px[64 * FogMap.SIZE + 64]);
            assertEquals(Tone.FOG, px[0], "가 보지 않은 곳은 안개");
            assertTrue(Arrays.stream(px).anyMatch(t -> t == Tone.CITY), "하르덴(도시)이 보인다");
        }
    }

    @Test
    void tonesFollowRegionCharacter() throws Exception {
        try (TestWorld w = new TestWorld()) {
            assertEquals(Tone.DESERT, FogMap.tone(w.s.regions.byId("sand_sea")));
            assertEquals(Tone.SEA, FogMap.tone(w.s.regions.byId("eastern_sea")));
            assertEquals(Tone.MIST, FogMap.tone(w.s.regions.byId("mist_wall")));
            assertEquals(Tone.CRATER, FogMap.tone(w.s.regions.byId("fallen_crater")));
        }
    }
}
