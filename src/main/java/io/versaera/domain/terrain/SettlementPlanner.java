package io.versaera.domain.terrain;

import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;

import java.util.*;

/**
 * 도시 · 랜드마크 배치 (WLD-02, ORIGINAL · 순수 계산). 원작 도시 지도를 베끼지 않고 지역 성격으로 새로 짓는다.
 * <ul>
 *   <li>도시(city · outpost · fortress 태그): 가운데 광장(우물) + 32 블록 간격 길 + 길가 건물(장식 건물, 하우징 아님) + 요새 · 전초기지는 성벽</li>
 *   <li>NPC 일과 장소 둘레 4 블록에는 건물을 짓지 않는다 (NPC 가 벽 안에 서지 않게)</li>
 *   <li>지역마다 랜드마크 하나 (탑 · 등대 · 첨탑 · 조각상 · 모루 기념비 · 오아시스 · 거상의 머리 · 아치 · 얼음 첨탑 · 오벨리스크 · 돌 고리 · 풍차 탑)</li>
 * </ul>
 * 결과는 "열(column)" 함수 묶음: 생성기가 청크마다 그 청크에 걸친 구조물의 열만 그린다 → 메모리에 블록 목록을 쌓지 않는다.
 * 같은 시드 = 같은 배치. 불변 객체라 여러 생성 스레드에서 함께 써도 안전하다.
 */
public final class SettlementPlanner {
    /** 블록 놓기 (재질은 Material 이름 문자열) */
    public interface Sink {
        void set(int x, int y, int z, String material);
    }

    public enum Kind { PLAZA, ROAD, BUILDING, WALL, LANDMARK }

    /** 구조물 하나: 영역(포함) + 열 그리기 */
    public abstract static class Structure {
        public final Kind kind;
        public final int minX, minZ, maxX, maxZ;
        public final String region;

        Structure(Kind kind, String region, int minX, int minZ, int maxX, int maxZ) {
            this.kind = kind;
            this.region = region;
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        public boolean covers(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public boolean overlaps(Structure o, int gap) {
            return minX - gap <= o.maxX && maxX + gap >= o.minX && minZ - gap <= o.maxZ && maxZ + gap >= o.minZ;
        }

        /** (x, z) 열을 그린다. ground = 그 열의 지형 높이 */
        public abstract void column(int x, int z, IntBinaryHeight ground, Sink sink);
    }

    @FunctionalInterface
    public interface IntBinaryHeight {
        int at(int x, int z);
    }

    /** 지역 성격별 재질 */
    record Style(String wall, String corner, String roof, String floor, String road, String trim, String window) {
        static Style of(Region r, RegionIndex idx) {
            Set<String> t = new HashSet<>(r.tags());
            for (Region p = r.parent() == null ? null : idx.byId(r.parent()); p != null; p = p.parent() == null ? null : idx.byId(p.parent())) t.addAll(p.tags());
            if (t.contains("desert")) return new Style("SANDSTONE", "CUT_SANDSTONE", "SMOOTH_SANDSTONE", "SMOOTH_SANDSTONE", "SMOOTH_SANDSTONE", "CHISELED_SANDSTONE", "GLASS_PANE");
            if (t.contains("coast")) return new Style("WHITE_TERRACOTTA", "STRIPPED_OAK_LOG", "BLUE_TERRACOTTA", "SPRUCE_PLANKS", "SMOOTH_STONE", "OAK_PLANKS", "GLASS_PANE");
            if (t.contains("mountain")) return new Style("STONE_BRICKS", "POLISHED_DEEPSLATE", "DEEPSLATE_TILES", "STONE_BRICKS", "COBBLED_DEEPSLATE", "CHISELED_STONE_BRICKS", "IRON_BARS");
            if (t.contains("highland") || t.contains("forest") || t.contains("artisan")) return new Style("SPRUCE_PLANKS", "STRIPPED_SPRUCE_LOG", "DARK_OAK_PLANKS", "SPRUCE_PLANKS", "STONE_BRICKS", "STRIPPED_DARK_OAK_LOG", "GLASS_PANE");
            if (t.contains("scholar")) return new Style("QUARTZ_BLOCK", "QUARTZ_PILLAR", "PURPUR_BLOCK", "SMOOTH_QUARTZ", "POLISHED_ANDESITE", "CHISELED_QUARTZ_BLOCK", "GLASS_PANE");
            if (t.contains("fortress") || t.contains("outpost")) return new Style("COBBLESTONE", "STRIPPED_OAK_LOG", "SPRUCE_PLANKS", "OAK_PLANKS", "GRAVEL", "MOSSY_COBBLESTONE", "IRON_BARS");
            return new Style("OAK_PLANKS", "STRIPPED_OAK_LOG", "BRICKS", "OAK_PLANKS", "STONE_BRICKS", "COBBLESTONE", "GLASS_PANE");
        }
    }

    private final List<Structure> structures;

    private SettlementPlanner(List<Structure> structures) {
        this.structures = List.copyOf(structures);
    }

    public List<Structure> structures() {
        return structures;
    }

    /** 이 사각형(청크)에 걸친 구조물 */
    public List<Structure> in(int minX, int minZ, int maxX, int maxZ) {
        List<Structure> out = new ArrayList<>();
        for (Structure s : structures) if (s.minX <= maxX && s.maxX >= minX && s.minZ <= maxZ && s.maxZ >= minZ) out.add(s);
        return out;
    }

    /**
     * @param keepClear 비워 둘 점 (NPC 일과 장소) — {x, z}
     */
    public static SettlementPlanner plan(RegionIndex regions, String world, long seed, List<int[]> keepClear) {
        List<Structure> out = new ArrayList<>();
        for (Region r : regions.all()) {
            if (!r.world().equals(world)) continue;
            Set<String> t = r.tags();
            // 땅속 지역(수로 · 매몰 도시)에는 도시를 짓지 않고, 지표에 랜드마크(반쯤 묻힌 오벨리스크 등)만 둔다
            boolean town = r.maxY() >= 64 && (t.contains("city") || t.contains("outpost") || t.contains("fortress"));
            SplittableRandom rng = new SplittableRandom(seed ^ r.id().hashCode() * 0x9E3779B97F4A7C15L);
            Style st = Style.of(r, regions);
            int cx = (r.minX() + r.maxX()) / 2, cz = (r.minZ() + r.maxZ()) / 2;
            Structure lm = landmark(r, st, cx, cz, town);
            if (town) town(out, r, st, cx, cz, rng, keepClear, lm);
            if (lm != null) out.add(lm);   // 마지막에 그려서 길 · 광장 위에 선다
            if (t.contains("wall")) out.add(new LongWall(r, st));   // 페드라 성벽 · 알 수 없는 장벽 · 추방의 장벽
        }
        return new SettlementPlanner(out);
    }

    // ------------------------------------------------------------------ 도시
    private static void town(List<Structure> out, Region r, Style st, int cx, int cz, SplittableRandom rng, List<int[]> keepClear, Structure landmark) {
        int half = Math.min(r.maxX() - r.minX(), r.maxZ() - r.minZ()) / 2;
        int radius = Math.max(40, Math.min(140, half - 12));
        radius = radius / 32 * 32;
        List<Structure> roads = new ArrayList<>();
        Structure plaza = new Plaza(r.id(), cx, cz, 10, st);
        // 길: 32 블록 간격 격자 (가운데 두 길은 넓게)
        for (int k = -radius / 32; k <= radius / 32; k++) {
            int w = k == 0 ? 2 : 1;
            roads.add(new Road(r.id(), cx + k * 32 - w, cz - radius, cx + k * 32 + w, cz + radius, st));
            roads.add(new Road(r.id(), cx - radius, cz + k * 32 - w, cx + radius, cz + k * 32 + w, st));
        }
        out.addAll(roads);
        out.add(plaza);   // 길 다음에 그려서 우물이 남는다
        // 건물: 길로 나뉜 칸마다 네 귀퉁이에 하나씩 (크기 · 높이는 시드)
        List<Structure> buildings = new ArrayList<>();
        for (int gx = -radius / 32; gx < radius / 32; gx++)
            for (int gz = -radius / 32; gz < radius / 32; gz++) {
                int x0 = cx + gx * 32 + 3, z0 = cz + gz * 32 + 3;   // 칸 안쪽 (길 폭 빼고)
                for (int q = 0; q < 4; q++) {
                    if (rng.nextInt(10) < 2) continue;   // 빈터도 남긴다
                    int w = 5 + rng.nextInt(5), d = 5 + rng.nextInt(5), h = 4 + rng.nextInt(4);
                    int bx = q % 2 == 0 ? x0 + 1 : x0 + 26 - w, bz = q < 2 ? z0 + 1 : z0 + 26 - d;
                    // 문은 가까운 길 쪽
                    char door = q % 2 == 0 ? (q < 2 ? 'W' : 'W') : 'E';
                    Building b = new Building(r.id(), bx, bz, bx + w - 1, bz + d - 1, h, door, st);
                    if (!inside(r, b, 2)) continue;
                    boolean clash = false;
                    if (b.overlaps(plaza, 2) || (landmark != null && b.overlaps(landmark, 2))) clash = true;
                    for (Structure o : roads) if (b.overlaps(o, 0)) clash = true;
                    for (Structure o : buildings) if (b.overlaps(o, 1)) clash = true;
                    for (int[] k : keepClear) if (k[0] >= b.minX - 4 && k[0] <= b.maxX + 4 && k[1] >= b.minZ - 4 && k[1] <= b.maxZ + 4) clash = true;
                    if (!clash) buildings.add(b);
                }
            }
        out.addAll(buildings);
        if (r.tags().contains("fortress") || r.tags().contains("outpost")) {
            int wr = radius + 4;
            out.add(new Wall(r.id(), cx, cz, wr, st));
        }
    }

    private static boolean inside(Region r, Structure s, int margin) {
        return s.minX >= r.minX() + margin && s.maxX <= r.maxX() - margin && s.minZ >= r.minZ() + margin && s.maxZ <= r.maxZ() - margin;
    }

    static final class Plaza extends Structure {
        private final int cx, cz;
        private final Style st;

        Plaza(String region, int cx, int cz, int half, Style st) {
            super(Kind.PLAZA, region, cx - half, cz - half, cx + half, cz + half);
            this.cx = cx;
            this.cz = cz;
            this.st = st;
        }

        @Override
        public void column(int x, int z, IntBinaryHeight ground, Sink s) {
            int y = ground.at(cx, cz);
            s.set(x, y, z, (x + z) % 2 == 0 ? st.road() : st.trim());
            for (int dy = 1; dy <= 3; dy++) s.set(x, y + dy, z, "AIR");
            int dx = Math.abs(x - cx), dz = Math.abs(z - cz);
            if (dx <= 1 && dz <= 1) {   // 우물
                s.set(x, y, z, dx == 0 && dz == 0 ? "WATER" : st.trim());
                if (dx == 1 || dz == 1) s.set(x, y + 1, z, st.trim());
            }
        }
    }

    static final class Road extends Structure {
        private final Style st;

        Road(String region, int x1, int z1, int x2, int z2, Style st) {
            super(Kind.ROAD, region, x1, z1, x2, z2);
            this.st = st;
        }

        @Override
        public void column(int x, int z, IntBinaryHeight ground, Sink s) {
            int y = ground.at(x, z);
            s.set(x, y, z, st.road());
            for (int dy = 1; dy <= 3; dy++) s.set(x, y + dy, z, "AIR");
        }
    }

    /** 장식 건물: 바닥 · 벽(모서리 기둥) · 창 · 문 자리 · 낮은 지붕. 안은 비어 있다 (하우징 아님) */
    static final class Building extends Structure {
        final int height;
        private final char door;
        private final Style st;

        Building(String region, int x1, int z1, int x2, int z2, int height, char door, Style st) {
            super(Kind.BUILDING, region, x1, z1, x2, z2);
            this.height = height;
            this.door = door;
            this.st = st;
        }

        @Override
        public void column(int x, int z, IntBinaryHeight ground, Sink s) {
            int base = ground.at((minX + maxX) / 2, (minZ + maxZ) / 2);
            int g = ground.at(x, z);
            for (int y = Math.min(g, base) - 2; y < base; y++) s.set(x, y, z, st.trim());   // 기초 (비탈 메우기)
            s.set(x, base, z, st.floor());
            boolean edgeX = x == minX || x == maxX, edgeZ = z == minZ || z == maxZ;
            boolean corner = edgeX && edgeZ, wall = edgeX || edgeZ;
            int midX = (minX + maxX) / 2, midZ = (minZ + maxZ) / 2;
            boolean doorCol = switch (door) {
                case 'E' -> x == maxX && z == midZ;
                case 'N' -> z == minZ && x == midX;
                case 'S' -> z == maxZ && x == midX;
                default -> x == minX && z == midZ;
            };
            for (int dy = 1; dy <= height; dy++) {
                String m;
                if (!wall) m = "AIR";
                else if (corner) m = st.corner();
                else if (doorCol && dy <= 2) m = "AIR";
                else if (dy == 2 && ((edgeX ? z : x) % 3 == 0)) m = st.window();
                else m = st.wall();
                s.set(x, base + dy, z, m);
            }
            // 지붕: 가장자리에서 안쪽으로 한 칸씩 올라가는 낮은 지붕
            int inset = Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
            int top = base + height + 1 + Math.min(inset, 2);
            for (int y = base + height + 1; y <= top; y++) s.set(x, y, z, y == top ? st.roof() : (wall ? st.roof() : "AIR"));
        }
    }

    static final class Wall extends Structure {
        private final int cx, cz, r;
        private final Style st;

        Wall(String region, int cx, int cz, int r, Style st) {
            super(Kind.WALL, region, cx - r, cz - r, cx + r, cz + r);
            this.cx = cx;
            this.cz = cz;
            this.r = r;
            this.st = st;
        }

        @Override
        public boolean covers(int x, int z) {
            return super.covers(x, z) && (Math.abs(x - cx) == r || Math.abs(z - cz) == r);
        }

        @Override
        public void column(int x, int z, IntBinaryHeight ground, Sink s) {
            if (Math.abs(x - cx) != r && Math.abs(z - cz) != r) return;
            boolean gate = Math.abs(x - cx) <= 2 || Math.abs(z - cz) <= 2;   // 큰길 끝은 성문
            int y = ground.at(x, z);
            for (int dy = 1; dy <= 6; dy++) s.set(x, y + dy, z, gate && dy <= 4 ? "AIR" : (dy == 6 && (x + z) % 2 == 0 ? "AIR" : st.wall()));
        }
    }

    /** 지역 상자의 긴 축을 따라 가운데에 쌓는 큰 방벽. 200 블록마다 통로가 있다 */
    static final class LongWall extends Structure {
        private final boolean alongX;
        private final int mid;
        private final Style st;

        LongWall(Region r, Style st) {
            super(Kind.WALL, r.id(), r.minX(), r.minZ(), r.maxX(), r.maxZ());
            this.alongX = r.maxX() - r.minX() >= r.maxZ() - r.minZ();
            this.mid = alongX ? (r.minZ() + r.maxZ()) / 2 : (r.minX() + r.maxX()) / 2;
            this.st = st;
        }

        @Override
        public boolean covers(int x, int z) {
            return super.covers(x, z) && Math.abs((alongX ? z : x) - mid) <= 1;
        }

        @Override
        public void column(int x, int z, IntBinaryHeight ground, Sink s) {
            int off = (alongX ? z : x) - mid;
            if (Math.abs(off) > 1) return;
            int along = alongX ? x - minX : z - minZ;
            boolean gate = Math.floorMod(along, 200) >= 97 && Math.floorMod(along, 200) <= 103;
            int y = ground.at(x, z);
            for (int dy = 1; dy <= 10; dy++) s.set(x, y + dy, z, gate && dy <= 6 ? "AIR" : st.wall());
            if (off != 0 && along % 2 == 0) s.set(x, y + 11, z, st.wall());   // 성가퀴
            if (Math.floorMod(along, 50) == 0) for (int dy = 1; dy <= 14; dy++) s.set(x, y + dy, z, st.corner());   // 망루 기둥
        }
    }

    // ------------------------------------------------------------------ 랜드마크
    enum Shape { VOLCANO_CORE, FLOATING_ISLAND, TOWER, LIGHTHOUSE, SPIRE, STATUE, ANVIL, OASIS, KEEP, WATCHTOWER, COLOSSUS_HEAD, ARCH, ICE_SPIRE, OBELISK, STONE_CIRCLE, WINDMILL, ENTRANCE, TOWER_STUMP }

    private static final Map<String, Shape> LANDMARKS = Map.ofEntries(
            Map.entry("harden", Shape.TOWER), Map.entry("morata_free_city", Shape.STATUE), Map.entry("thor_deep_hammer", Shape.ANVIL),
            Map.entry("rosaim_harbor", Shape.LIGHTHOUSE), Map.entry("astra_academy", Shape.SPIRE), Map.entry("azil_oasis", Shape.OASIS),
            Map.entry("nehales_bastion", Shape.KEEP), Map.entry("risvel_outpost", Shape.WATCHTOWER), Map.entry("fallen_crater", Shape.COLOSSUS_HEAD),
            Map.entry("calamor_ruins", Shape.ARCH), Map.entry("niflheim_wastes", Shape.ICE_SPIRE), Map.entry("buried_city", Shape.OBELISK),
            Map.entry("mist_wall", Shape.ARCH), Map.entry("serven_granary", Shape.WINDMILL), Map.entry("north_reach", Shape.STONE_CIRCLE),
            Map.entry("brent_highlands", Shape.STONE_CIRCLE), Map.entry("west_frontier", Shape.STONE_CIRCLE), Map.entry("sand_sea", Shape.OBELISK),
            Map.entry("lavias", Shape.FLOATING_ISLAND), Map.entry("sendeim_valley", Shape.STONE_CIRCLE), Map.entry("baroque_range", Shape.WATCHTOWER),
            Map.entry("plains_of_despair", Shape.OBELISK), Map.entry("serabourg", Shape.TOWER), Map.entry("baran_village", Shape.WINDMILL),
            Map.entry("britten_alliance", Shape.STATUE), Map.entry("jigolas", Shape.VOLCANO_CORE), Map.entry("aren_castle", Shape.KEEP),
            Map.entry("sisley_castle", Shape.TOWER), Map.entry("odein_fortress", Shape.KEEP), Map.entry("somren_free_city", Shape.SPIRE),
            Map.entry("yunopu_canyon", Shape.ARCH), Map.entry("furghol_ruins", Shape.KEEP),
            Map.entry("tolen_lands", Shape.ARCH), Map.entry("orc_land", Shape.STONE_CIRCLE), Map.entry("hunters_hill", Shape.WATCHTOWER),
            Map.entry("birch_lake", Shape.WATCHTOWER),
            // 지리 문서의 명소 (작은 표시 지역)
            Map.entry("dawn_city", Shape.SPIRE), Map.entry("light_tower", Shape.LIGHTHOUSE), Map.entry("freya_statue", Shape.STATUE),
            Map.entry("morata_art_hall", Shape.KEEP), Map.entry("garden_of_gods", Shape.OASIS), Map.entry("alcazar_bridge", Shape.ARCH),
            Map.entry("imbel_circle", Shape.STONE_CIRCLE), Map.entry("silent_tower", Shape.TOWER), Map.entry("struggle_road", Shape.ARCH),
            Map.entry("lu_sanctum", Shape.SPIRE), Map.entry("slave_bridge", Shape.ARCH), Map.entry("sky_tower_ruins", Shape.TOWER_STUMP),
            Map.entry("sun_altar", Shape.STONE_CIRCLE), Map.entry("roderick_labyrinth", Shape.STATUE), Map.entry("nukod_oasis", Shape.OASIS),
            Map.entry("desert_of_tranquility", Shape.OASIS));

    public static Set<String> landmarkRegions() {
        return LANDMARKS.keySet();
    }

    private static Structure landmark(Region r, Style st, int cx, int cz, boolean town) {
        Shape shape = LANDMARKS.get(r.id());
        if (shape == null && r.tags().contains("dungeon_site")) shape = Shape.ENTRANCE;   // 던전 입구: 돌 문틀
        if (shape == null && r.tags().contains("landmark")) shape = Shape.STONE_CIRCLE;
        if (shape == null) return null;
        // 도시는 광장 북동쪽 길 사이 칸 가운데, 그 밖은 지역 가운데
        int x = town ? cx + 16 : cx, z = town ? cz - 16 : cz;
        int half = switch (shape) {
            case FLOATING_ISLAND -> 40;
            case VOLCANO_CORE -> 8;
            case COLOSSUS_HEAD -> 14;
            case STONE_CIRCLE, OASIS -> 9;
            case ARCH -> 8;
            case KEEP -> 7;
            case TOWER_STUMP -> 12;
            default -> 5;
        };
        return new Landmark(r.id(), shape, x, z, half, st);
    }

    static final class Landmark extends Structure {
        final Shape shape;
        private final int cx, cz, half;
        private final Style st;

        Landmark(String region, Shape shape, int cx, int cz, int half, Style st) {
            super(Kind.LANDMARK, region, cx - half, cz - half, cx + half, cz + half);
            this.shape = shape;
            this.cx = cx;
            this.cz = cz;
            this.half = half;
            this.st = st;
        }

        private void fill(Sink s, int x, int z, int from, int to, String m) {
            for (int y = from; y <= to; y++) s.set(x, y, z, m);
        }

        @Override
        public void column(int x, int z, IntBinaryHeight ground, Sink s) {
            int y0 = ground.at(cx, cz), dx = x - cx, dz = z - cz;
            double d = Math.hypot(dx, dz);
            switch (shape) {
                case VOLCANO_CORE -> { if (d <= 7) { s.set(x, y0, z, "MAGMA_BLOCK"); s.set(x, y0 + 1, z, d <= 5 ? "LAVA" : "BASALT"); s.set(x, y0 + 2, z, "AIR"); } }
                case FLOATING_ISLAND -> { // 하늘섬 라비아스: 땅에서 떨어진 높이(210)에 떠 있는 섬 + 가운데 탑. 가장자리는 부서져 있다 (파편이 분화구로 떨어짐)
                    double e = Math.hypot(dx, dz * 1.2);
                    boolean broken = dx > 28 && Math.floorMod(dz * 7 + dx * 3, 5) == 0;
                    if (e <= 40 && !broken) { int top = 212, depth = (int) Math.round((1 - (e * e) / 1600.0) * 18);
                        for (int y = top - depth; y < top; y++) s.set(x, y, z, y >= top - 3 ? "DIRT" : "STONE");
                        s.set(x, top, z, e > 38 ? "STONE" : "GRASS_BLOCK");
                        if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) fill(s, x, z, top + 1, top + 16, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? "QUARTZ_BRICKS" : "AIR");
                        if (dx == 0 && dz == 0) s.set(x, top + 17, z, "SEA_LANTERN"); } }
                case TOWER -> { if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) { boolean shell = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    fill(s, x, z, y0, y0 + 24, shell ? (Math.abs(dx) == 3 && Math.abs(dz) == 3 ? st.corner() : st.wall()) : "AIR");
                    s.set(x, y0, z, st.floor()); s.set(x, y0 + 25, z, st.roof()); if (dx == 0 && dz == 0) s.set(x, y0 + 26, z, "BELL"); } }
                case LIGHTHOUSE -> { if (d <= 3.5) { fill(s, x, z, y0, y0 + 28, d > 2.5 ? ((y0 % 2 == 0) ? "WHITE_CONCRETE" : "WHITE_CONCRETE") : "AIR");
                    for (int y = y0; y <= y0 + 28; y += 6) if (d > 2.5) s.set(x, y, z, "RED_CONCRETE");
                    s.set(x, y0 + 29, z, d <= 1.5 ? "GLOWSTONE" : "GLASS"); s.set(x, y0 + 30, z, st.roof()); } }
                case SPIRE -> { int top = y0 + 40 - (int) Math.round(d * 7); if (d <= 4.5) fill(s, x, z, y0, top, d < 1 ? "AMETHYST_BLOCK" : st.wall()); }
                case STATUE -> { // 받침 + 사람 모양 (팔 벌린 조각상, 이름 없는 장인을 기리는 상)
                    if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) fill(s, x, z, y0, y0 + 2, st.trim());
                    if (Math.abs(dz) <= 1 && Math.abs(dx) <= 1) fill(s, x, z, y0 + 3, y0 + 10, "POLISHED_ANDESITE");
                    if (dz == 0 && Math.abs(dx) <= 4) s.set(x, y0 + 9, z, "POLISHED_ANDESITE");
                    if (Math.abs(dz) <= 1 && Math.abs(dx) <= 1) fill(s, x, z, y0 + 11, y0 + 13, "SMOOTH_STONE"); }
                case ANVIL -> { if (Math.abs(dx) <= 4 && Math.abs(dz) <= 2) fill(s, x, z, y0, y0 + 1, "POLISHED_BLACKSTONE");
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) fill(s, x, z, y0 + 2, y0 + 4, "IRON_BLOCK");
                    if (Math.abs(dx) <= 5 && Math.abs(dz) <= 2) fill(s, x, z, y0 + 5, y0 + 6, "IRON_BLOCK"); }
                case OASIS -> { if (d <= 6) { s.set(x, y0, z, "WATER"); s.set(x, y0 - 1, z, "SAND"); s.set(x, y0 + 1, z, "AIR"); }
                    else if (d <= 7) s.set(x, y0, z, "GRASS_BLOCK");
                    if ((Math.abs(dx) == 8 && dz == 0) || (Math.abs(dz) == 8 && dx == 0)) { fill(s, x, z, y0 + 1, y0 + 6, "JUNGLE_LOG"); s.set(x, y0 + 7, z, "JUNGLE_LEAVES"); } }
                case KEEP -> { if (Math.abs(dx) <= 7 && Math.abs(dz) <= 7) { boolean shell = Math.abs(dx) == 7 || Math.abs(dz) == 7;
                    fill(s, x, z, y0, y0 + 14, shell ? st.wall() : "AIR"); s.set(x, y0, z, st.floor());
                    if (shell && (x + z) % 2 == 0) s.set(x, y0 + 15, z, st.wall()); } }
                case WATCHTOWER -> { if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) { boolean leg = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    if (leg) fill(s, x, z, y0 + 1, y0 + 12, "SPRUCE_LOG"); s.set(x, y0 + 12, z, "SPRUCE_PLANKS");
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) s.set(x, y0 + 13, z, "SPRUCE_FENCE"); } }
                case COLOSSUS_HEAD -> { // 쓰러진 거상의 머리: 반쯤 묻힌 큰 돌 머리 (원작 인물 아님)
                    double e = Math.hypot(dx / 1.0, dz / 1.3);
                    if (e <= 12) { int h = (int) Math.round(Math.sqrt(144 - e * e)); fill(s, x, z, y0 - 2, y0 + h, e > 10 ? "MOSSY_STONE_BRICKS" : "STONE_BRICKS");
                        if (Math.abs(dz + 4) <= 1 && (Math.abs(dx - 4) <= 1 || Math.abs(dx + 4) <= 1)) s.set(x, y0 + h, z, "CRYING_OBSIDIAN"); } }
                case ARCH -> { if (Math.abs(dz) <= 1 && Math.abs(dx) <= 8) { boolean leg = Math.abs(dx) >= 6;
                    if (leg) fill(s, x, z, y0 + 1, y0 + 12, st.wall() .equals("SANDSTONE") ? "CHISELED_SANDSTONE" : "STONE_BRICKS");
                    fill(s, x, z, y0 + 11, y0 + 13, x % 3 == 0 ? "CRACKED_STONE_BRICKS" : "STONE_BRICKS"); } }
                case ICE_SPIRE -> { int top = y0 + 30 - (int) Math.round(d * 6); if (d <= 4.5) fill(s, x, z, y0, top, d < 2 ? "BLUE_ICE" : "PACKED_ICE"); }
                case OBELISK -> { int top = y0 + 18 - (int) Math.max(0, (Math.max(Math.abs(dx), Math.abs(dz)) - 1) * 6);
                    if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) fill(s, x, z, y0 - 4, top, Math.abs(dx) + Math.abs(dz) == 0 ? "CHISELED_SANDSTONE" : "CUT_SANDSTONE"); }
                case STONE_CIRCLE -> { for (int i = 0; i < 8; i++) { double a = Math.PI * 2 * i / 8;
                    if (x == cx + (int) Math.round(Math.cos(a) * 8) && z == cz + (int) Math.round(Math.sin(a) * 8)) fill(s, x, z, y0 + 1, y0 + 4 + i % 2, "MOSSY_COBBLESTONE"); }
                    if (dx == 0 && dz == 0) s.set(x, y0 + 1, z, "CHISELED_STONE_BRICKS"); }
                case ENTRANCE -> { // 땅으로 내려가는 돌 문틀 (안쪽 던전은 인스턴스)
                    if (Math.abs(dx) <= 3 && Math.abs(dz) <= 2) { boolean frame = Math.abs(dx) == 3 || dz == -2;
                        fill(s, x, z, y0 + 1, y0 + 6, frame ? "CHISELED_STONE_BRICKS" : "AIR");
                        if (!frame) { s.set(x, y0, z, "AIR"); s.set(x, y0 - 1, z, "AIR"); s.set(x, y0 - 2, z, "STONE_BRICK_STAIRS"); }
                        s.set(x, y0 + 7, z, "STONE_BRICK_SLAB"); } }
                case TOWER_STUMP -> { // 하늘로 오르는 탑의 무너진 밑동: 두꺼운 원통, 높이가 들쭉날쭉
                    if (d <= 12 && d >= 8) { int top = y0 + 20 + Math.floorMod(dx * 31 + dz * 17, 23);
                        fill(s, x, z, y0, top, Math.floorMod(dx + dz, 4) == 0 ? "CRACKED_STONE_BRICKS" : "STONE_BRICKS"); }
                    else if (d < 8) s.set(x, y0, z, "COBBLED_DEEPSLATE"); }
                case WINDMILL -> { if (d <= 3) fill(s, x, z, y0, y0 + 14, d > 2 ? "STONE_BRICKS" : "AIR");
                    if (dz == -4 && Math.abs(dx) <= 5) s.set(x, y0 + 12, z, "WHITE_WOOL");
                    if (dz == -4 && dx == 0) fill(s, x, z, y0 + 7, y0 + 17, "WHITE_WOOL"); }
            }
        }
    }
}
