package io.versaera.domain;

import io.versaera.content.ContentBundle;
import io.versaera.domain.terrain.Blueprint;
import io.versaera.domain.terrain.Medieval;
import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MedievalTest {
    private final RegionIndex regions = new RegionIndex(ContentBundle.fromClasspath(getClass().getClassLoader()).regions());

    private static Set<String> blocks(Blueprint b) {
        Set<String> out = new HashSet<>();
        for (int y = 0; y < b.h; y++) for (int z = 0; z < b.d; z++) for (int x = 0; x < b.w; x++) if (b.get(x, y, z) != null) out.add(b.get(x, y, z));
        return out;
    }

    @Test
    void rotationTurnsPositionsAndBlockStatesClockwise() {
        Blueprint b = new Blueprint(3, 1, 2);
        b.set(0, 0, 0, "oak_stairs[facing=north,half=bottom,shape=straight]");
        b.set(2, 0, 1, "oak_log[axis=x]");
        Blueprint r = b.rotated(1);
        assertEquals(2, r.w);
        assertEquals(3, r.d);
        assertEquals("oak_stairs[facing=east,half=bottom,shape=straight]", r.get(1, 0, 0));
        assertEquals("oak_log[axis=z]", r.get(0, 0, 2));
        assertEquals("glass_pane[south=true,west=true]", Blueprint.rotateBlock("glass_pane[east=true,south=true]", 1).replace("west=true,south=true", "south=true,west=true"));
        Blueprint full = b.rotated(4);
        assertEquals(b.get(0, 0, 0), full.get(0, 0, 0));
    }

    @Test
    void buildingsHaveDoorsWindowsRoofsAndNoNamespacedOrUppercaseIds() {
        SplittableRandom rng = new SplittableRandom(1);
        for (Region r : regions.all()) {
            Medieval.Palette p = Medieval.Palette.of(r, regions);
            for (Blueprint b : List.of(Medieval.house(p, 7, 6, 2, rng), Medieval.tavernMarked(p, rng), Medieval.smithyMarked(p, rng), Medieval.chapel(p, rng), Medieval.guildHall(p, rng), Medieval.marketHall(p, rng))) {
                Set<String> s = blocks(b);
                for (String id : s) {
                    assertFalse(id.contains(":") || !id.equals(id.toLowerCase(Locale.ROOT)), id);
                    assertFalse(id.startsWith("cut_sandstone_stairs"), id);
                }
                assertTrue(s.stream().anyMatch(id -> id.contains("lantern")), "등불");
            }
            Blueprint house = Medieval.house(p, 7, 6, 2, rng);
            Set<String> s = blocks(house);
            assertTrue(s.stream().anyMatch(id -> id.contains("_door[")), "문");
            assertTrue(s.stream().anyMatch(id -> id.contains("glass_pane") || id.contains("trapdoor")), "창");
            if (!p.flat()) assertTrue(s.stream().anyMatch(id -> id.contains("_stairs[")), "경사 지붕");
        }
    }
}
