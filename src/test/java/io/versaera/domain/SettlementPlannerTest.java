package io.versaera.domain;

import io.versaera.content.ContentBundle;
import io.versaera.domain.terrain.SettlementPlanner;
import io.versaera.domain.terrain.SettlementPlanner.Kind;
import io.versaera.domain.terrain.TerrainModel;
import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SettlementPlannerTest {
    private final ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
    private final RegionIndex regions = new RegionIndex(c.regions());
    private final TerrainModel terrain = new TerrainModel(regions, "world", 42);

    private List<int[]> npcPlaces() {
        List<int[]> out = new ArrayList<>();
        c.places().values().forEach(m -> m.values().forEach(p -> out.add(new int[]{(int) Math.floor(p.x()), (int) Math.floor(p.z())})));
        return out;
    }

    private SettlementPlanner plan(long seed) {
        return SettlementPlanner.plan(regions, "world", seed, npcPlaces());
    }

    @Test
    void everyTownHasStreetsBuildingsAndALandmark() {
        SettlementPlanner main = plan(42), realms = SettlementPlanner.plan(regions, "versa_realms", 42, npcPlaces());
        List<SettlementPlanner.Structure> all = new ArrayList<>(main.structures());
        all.addAll(realms.structures());
        record Both(List<SettlementPlanner.Structure> structures) {}
        Both p = new Both(all);
        for (Region r : regions.all()) {
            if (!(r.tags().contains("city") || r.tags().contains("outpost") || r.tags().contains("fortress"))) continue;
            long houses = p.structures().stream().filter(s -> s.kind == Kind.BUILDING && s.region.equals(r.id())).count();
            assertTrue(houses >= 8, r.id() + " 건물 " + houses);
            assertTrue(p.structures().stream().anyMatch(s -> s.kind == Kind.PLAZA && s.region.equals(r.id())), r.id());
        }
        for (String id : SettlementPlanner.landmarkRegions())
            assertTrue(p.structures().stream().anyMatch(s -> s.kind == Kind.LANDMARK && s.region.equals(id)), "랜드마크 없음: " + id);
        assertTrue(p.structures().stream().anyMatch(s -> s.kind == Kind.WALL && s.region.equals("nehales_bastion")), "요새는 성벽");
        for (Region r : regions.all()) {
            if (r.tags().contains("wall")) assertTrue(p.structures().stream().anyMatch(s -> s.kind == Kind.WALL && s.region.equals(r.id())), "장벽 없음: " + r.id());
            if (r.tags().contains("dungeon_site") || r.tags().contains("landmark") || r.tags().contains("portal"))
                assertTrue(p.structures().stream().anyMatch(s -> s.kind == Kind.LANDMARK && s.region.equals(r.id())), "입구 · 명소 없음: " + r.id());
        }
    }

    @Test
    void buildingsStayInsideTheirRegionAndNeverBlockStreetsOrNpcs() {
        SettlementPlanner p = plan(7);
        List<SettlementPlanner.Structure> b = p.structures().stream().filter(s -> s.kind == Kind.BUILDING).toList();
        List<SettlementPlanner.Structure> blockers = p.structures().stream().filter(s -> s.kind == Kind.ROAD || s.kind == Kind.PLAZA || s.kind == Kind.LANDMARK).toList();
        for (var x : b) {
            Region r = regions.byId(x.region);
            assertTrue(x.minX >= r.minX() && x.maxX <= r.maxX() && x.minZ >= r.minZ() && x.maxZ <= r.maxZ(), "지역 밖 건물 " + x.region);
            for (var o : blockers) if (o.region.equals(x.region)) assertFalse(x.overlaps(o, 0), "건물이 길 · 광장 · 랜드마크를 막음: " + x.region);
            for (var y : b) if (y != x) assertFalse(x.overlaps(y, 0), "건물끼리 겹침");
            for (int[] n : npcPlaces()) assertFalse(x.covers(n[0], n[1]), "NPC 자리에 건물: " + x.region);
        }
    }

    @Test
    void sameSeedSamePlanAndColumnsDrawWallsAndRoofs() {
        var a = plan(3).structures();
        var b = plan(3).structures();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).minX + ":" + a.get(i).minZ + ":" + a.get(i).maxX, b.get(i).minX + ":" + b.get(i).minZ + ":" + b.get(i).maxX);
        var house = a.stream().filter(s -> s.kind == Kind.BUILDING).findFirst().orElseThrow();
        Map<Integer, String> col = new TreeMap<>();
        house.column(house.minX, house.minZ, (x, z) -> 70, (x, y, z, m) -> col.put(y, m));
        assertTrue(col.values().stream().anyMatch(m -> !m.equalsIgnoreCase("air")), "가장자리");
        Map<Integer, String> mid = new TreeMap<>();
        house.column((house.minX + house.maxX) / 2, (house.minZ + house.maxZ) / 2, (x, z) -> 70, (x, y, z, m) -> mid.put(y, m));
        assertEquals("air", mid.get(71), "안은 비어 있다 (장식 건물)");
        int roof = mid.entrySet().stream().filter(e -> !e.getValue().equals("air")).mapToInt(Map.Entry::getKey).max().orElseThrow();
        assertTrue(roof >= 75, "지붕 " + roof);
    }

    @Test
    void startCitySpawnIsOpenGroundNotInsideAFountainOrBuilding() {
        SettlementPlanner main = plan(42), realms = SettlementPlanner.plan(regions, "versa_realms", 42, npcPlaces());
        for (var city : c.origins().cities()) {
            Region r = regions.byId(city.region());
            int[] sp = SettlementPlanner.spawnPoint(r);
            for (SettlementPlanner p : List.of(main, realms))
                for (var st : p.in(sp[0] - 2, sp[1] - 2, sp[0] + 2, sp[1] + 2))
                    if (st.kind != Kind.ROAD && st.kind != Kind.PLAZA)
                        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                            assertFalse(st.covers(sp[0] + dx, sp[1] + dz), city.id() + " 시작 지점이 " + st.kind + " 안");
        }
    }

    @Test
    void writesHardenPlanImage() throws Exception {
        Region h = regions.byId("harden");
        int w = h.maxX() - h.minX() + 1, d = h.maxZ() - h.minZ() + 1;
        BufferedImage img = new BufferedImage(w, d, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < d; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, 0x6aa84f);
        for (var s : plan(42).in(h.minX(), h.minZ(), h.maxX(), h.maxZ())) {
            int rgb = switch (s.kind) { case ROAD -> 0x9a948c; case PLAZA -> 0xd8c58a; case BUILDING -> 0xa0522d; case DECOR -> 0xe0b040; case WALL -> 0x444444; case LANDMARK -> 0xc9a227; };
            for (int x = Math.max(h.minX(), s.minX); x <= Math.min(h.maxX(), s.maxX); x++)
                for (int z = Math.max(h.minZ(), s.minZ); z <= Math.min(h.maxZ(), s.maxZ); z++)
                    if (s.covers(x, z)) img.setRGB(x - h.minX(), z - h.minZ(), rgb);
        }
        File out = new File("build/harden-plan.png");
        out.getParentFile().mkdirs();
        assertTrue(ImageIO.write(img, "png", out));
    }
}
