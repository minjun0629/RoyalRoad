package io.versaera.domain.terrain;

import io.versaera.domain.world.Region;
import io.versaera.domain.terrain.Medieval.Palette;
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

    public enum Kind { PLAZA, ROAD, BUILDING, DECOR, WALL, LANDMARK }

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
            boolean town = isTown(r);
            SplittableRandom rng = new SplittableRandom(seed ^ r.id().hashCode() * 0x9E3779B97F4A7C15L);
            Style st = Style.of(r, regions);
            Palette pal = Palette.of(r, regions);
            int cx = (r.minX() + r.maxX()) / 2, cz = (r.minZ() + r.maxZ()) / 2;
            Structure lm = landmark(r, st, cx, cz, town);
            if (town) town(out, r, pal, cx, cz, rng, keepClear, lm);
            if (lm != null) out.add(lm);   // 마지막에 그려서 길 · 광장 위에 선다
            if (t.contains("wall")) out.add(new LongWall(r, st));   // 페드라 성벽 · 알 수 없는 장벽 · 추방의 장벽
        }
        return new SettlementPlanner(out);
    }

    /** 도시를 짓는 지역인가 (city · outpost · fortress, 지표) */
    public static boolean isTown(Region r) {
        Set<String> t = r.tags();
        return r.maxY() >= 64 && (t.contains("city") || t.contains("outpost") || t.contains("fortress"));
    }

    /**
     * 도시의 길 격자: {가운데 x, 가운데 z, 반지름}. 길은 x = cx + k·32 · z = cz + k·32 (|k| ≤ 반지름/32) 에 반지름 끝까지 난다.
     * 길 · 광장 위에는 건물을 짓지 않는다 → NPC 자리를 길 위에 두면 이미 만들어진 세계에서도 벽 안에 서지 않는다.
     */
    public static int[] townGrid(Region r) {
        int half = Math.min(r.maxX() - r.minX(), r.maxZ() - r.minZ()) / 2;
        int radius = Math.max(40, Math.min(140, half - 12));
        return new int[]{(r.minX() + r.maxX()) / 2, (r.minZ() + r.maxZ()) / 2, radius / 32 * 32};
    }

    /**
     * 시작 · 부활 지점 {x, z, 바라볼 방향(도)}. 도시는 광장 안 남쪽 — 분수(반지름 5)와 노점 줄 사이의 큰길 위, 분수를 바라본다.
     * 도시가 아니면 지역 가운데에서 랜드마크를 피해 남쪽으로 비켜선다.
     */
    public static int[] spawnPoint(Region r) {
        int cx = (r.minX() + r.maxX()) / 2, cz = (r.minZ() + r.maxZ()) / 2;
        if (isTown(r)) return new int[]{cx, cz + 8, 180};
        return new int[]{cx, cz + 16, 180};
    }

    // ------------------------------------------------------------------ 도시
    /**
     * 중세 도시: 분수 · 노점 · 가로등이 있는 광장, 돌을 섞어 깐 길, 길을 따라 늘어선 목조 골조 집(문은 길 쪽),
     * 광장 둘레에 성당 · 여관 · 대장간, 바깥에 탑과 성문이 있는 성벽 (설계도 = Medieval)
     */
    private static void town(List<Structure> out, Region r, Palette p, int cx, int cz, SplittableRandom rng, List<int[]> keepClear, Structure landmark) {
        int radius = townGrid(r)[2], n = radius / 32;
        List<Structure> roads = new ArrayList<>();
        Structure plaza = new Plaza(r.id(), cx, cz, 10, p);
        // 길: 32 블록 간격 격자 (가운데 두 길은 넓게). 성벽 문까지 이어진다
        for (int k = -n; k <= n; k++) {
            int w = k == 0 ? 2 : 1, reach = Math.abs(k) == n ? radius + 1 : radius + 8;   // 바깥 두 길은 성벽 안 순환로
            roads.add(new Road(r.id(), cx + k * 32 - w, cz - reach, cx + k * 32 + w, cz + reach, p));
            roads.add(new Road(r.id(), cx - reach, cz + k * 32 - w, cx + reach, cz + k * 32 + w, p));
        }
        out.addAll(roads);
        out.add(plaza);
        List<Structure> placed = new ArrayList<>();   // 건물 · 장식 (서로 겹치지 않게)
        java.util.function.Predicate<Structure> free = b -> {
            if (!inside(r, b, 2)) return false;
            if (b.kind == Kind.BUILDING && (b.overlaps(plaza, 2))) return false;
            if (landmark != null && b.overlaps(landmark, 2)) return false;
            boolean onPlaza = b.minX >= plaza.minX && b.maxX <= plaza.maxX && b.minZ >= plaza.minZ && b.maxZ <= plaza.maxZ;
            if (!onPlaza) for (Structure o : roads) if (b.overlaps(o, 0)) return false;
            for (Structure o : placed) if (b.overlaps(o, 0)) return false;
            int gap = 1;   // NPC 자리는 길 위라 건물이 덮지만 않으면 된다
            for (int[] k : keepClear) if (k[0] >= b.minX - gap && k[0] <= b.maxX + gap && k[1] >= b.minZ - gap && k[1] <= b.maxZ + gap) return false;
            return true;
        };
        // 광장: 가운데 분수, 네 귀퉁이에 노점 (가운데를 본다), 분수 둘레 가로등
        tryPlace(placed, free, new Built(Kind.DECOR, r.id(), cx - 5, cz - 5, Medieval.fountain(), cx, cz, p));
        for (int sx : new int[]{-1, 1})
            for (int sz : new int[]{-1, 1}) {
                Blueprint st = Medieval.stall(p, rng).rotated(sz < 0 ? 2 : 0);
                int x = sx < 0 ? cx - 10 : cx + 6, z = sz < 0 ? cz - 10 : cz + 7;
                tryPlace(placed, free, new Built(Kind.DECOR, r.id(), x, z, st, cx, cz, p));
                tryPlace(placed, free, new Built(Kind.DECOR, r.id(), cx + sx * 6, cz + sz * 6, Medieval.lamp(p), cx, cz, p));
            }
        // 광장 둘레의 큰 건물: 북서 칸 = 성당 (문이 남쪽 큰길), 남서 칸 = 여관, 남동 칸 = 대장간 (문이 북쪽 큰길)
        Blueprint chapel = Medieval.chapel(p, rng).rotated(2);
        tryPlace(placed, free, new Built(Kind.BUILDING, r.id(), cx - 28, cz - 3 - chapel.d, chapel, p));
        Blueprint tavern = Medieval.tavern(p, rng);
        tryPlace(placed, free, new Built(Kind.BUILDING, r.id(), cx - 15 - tavern.w, cz + 3, tavern, p));
        Blueprint smithy = Medieval.smithy(p, rng);
        tryPlace(placed, free, new Built(Kind.BUILDING, r.id(), cx + 16, cz + 3, smithy, p));
        // 집: 칸마다 북쪽 줄(문 = 북쪽 길) · 남쪽 줄(문 = 남쪽 길)로 늘어선다. 가운데는 뒷마당
        for (int gx = -n; gx < n; gx++)
            for (int gz = -n; gz < n; gz++) {
                int ix1 = cx + gx * 32 + 3, ix2 = cx + gx * 32 + 29, iz1 = cz + gz * 32 + 3, iz2 = cz + gz * 32 + 29;
                for (int row = 0; row < 2; row++) {
                    int x = ix1 + rng.nextInt(2);
                    while (true) {
                        int fw = 5 + rng.nextInt(5), fd = 5 + rng.nextInt(4), floors = 1 + rng.nextInt(10) / 4;   // 1층 40% · 2층 40% · 3층 20%
                        Blueprint h = Medieval.house(p, fw, fd, Math.min(3, floors), rng);
                        if (row == 1) h = h.rotated(2);
                        if (x + h.w - 1 > ix2) break;
                        int z = row == 0 ? iz1 : iz2 - h.d + 1;
                        if (rng.nextInt(10) >= 1) tryPlace(placed, free, new Built(Kind.BUILDING, r.id(), x, z, h, p));
                        x += h.w + rng.nextInt(2);
                    }
                }
            }
        // 가로등: 큰길을 따라 12 블록마다 길가에
        for (int k = -n; k <= n; k++) {
            int w = k == 0 ? 2 : 1;
            for (int t = -radius + 6; t <= radius - 6; t += 12) {
                tryPlace(placed, free, new Built(Kind.DECOR, r.id(), cx + k * 32 + w + 1, cz + t, Medieval.lamp(p), p));
                tryPlace(placed, free, new Built(Kind.DECOR, r.id(), cx + t, cz + k * 32 - w - 1, Medieval.lamp(p), p));
            }
        }
        out.addAll(placed);
        // 성벽: 도시 · 요새 · 전초기지 모두 (탑 · 성문)
        out.add(new Wall(r.id(), cx, cz, radius + 4, radius, p, keepClear));
    }

    private static void tryPlace(List<Structure> placed, java.util.function.Predicate<Structure> free, Structure s) {
        if (free.test(s)) placed.add(s);
    }

    private static boolean inside(Region r, Structure s, int margin) {
        return s.minX >= r.minX() + margin && s.maxX <= r.maxX() - margin && s.minZ >= r.minZ() + margin && s.maxZ <= r.maxZ() - margin;
    }

    /** 지형 흔들림: 같은 좌표 = 같은 값 */
    static int hash(int x, int z) {
        int h = x * 0x1f1f1f1f ^ z * 0x5bd1e995;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        return h ^ (h >>> 12);
    }

    static String pickAt(List<String> l, int x, int z) {
        return l.get(Math.floorMod(hash(x, z), l.size()));
    }

    /** 설계도 하나를 땅에 세운다: 기준 높이 = 기준점 지형, 비탈은 돌 기초로 메우고 위로 솟은 흙 · 나무는 걷어낸다 */
    static final class Built extends Structure {
        final Blueprint bp;
        private final int ax, az;
        private final Palette p;

        Built(Kind kind, String region, int minX, int minZ, Blueprint bp, Palette p) {
            this(kind, region, minX, minZ, bp, minX + bp.w / 2, minZ + bp.d / 2, p);
        }

        /** @param ax · az 기준 높이를 잴 점 (광장 장식은 광장 가운데) */
        Built(Kind kind, String region, int minX, int minZ, Blueprint bp, int ax, int az, Palette p) {
            super(kind, region, minX, minZ, minX + bp.w - 1, minZ + bp.d - 1);
            this.bp = bp;
            this.ax = ax;
            this.az = az;
            this.p = p;
        }

        @Override
        public void column(int x, int z, IntBinaryHeight ground, Sink s) {
            int base = ground.at(ax, az), g = ground.at(x, z), bx = x - minX, bz = z - minZ;
            boolean foot = bp.get(bx, 0, bz) != null;
            if (foot) for (int y = Math.min(g, base) - 2; y < base; y++) s.set(x, y, z, p.foundation());
            else if (g != base) {
                for (int y = g + 1; y < base; y++) s.set(x, y, z, "dirt");
                s.set(x, base, z, p.flat() ? "sand" : "grass_block");
            }
            int top = Math.max(g, base + bp.h + 3);
            for (int y = 0; base + y <= top; y++) {
                String b = bp.get(bx, y, bz);
                if (b != null) s.set(x, base + y, z, b);
                else if (y > 0) s.set(x, base + y, z, "air");
            }
        }
    }

    static final class Plaza extends Structure {
        private final int cx, cz;
        private final Palette p;

        Plaza(String region, int cx, int cz, int half, Palette p) {
            super(Kind.PLAZA, region, cx - half, cz - half, cx + half, cz + half);
            this.cx = cx;
            this.cz = cz;
            this.p = p;
        }

        @Override
        public void column(int x, int z, IntBinaryHeight ground, Sink s) {
            int y = ground.at(cx, cz), g = ground.at(x, z);
            for (int yy = g + 1; yy < y; yy++) s.set(x, yy, z, p.foundation());
            int ring = Math.max(Math.abs(x - cx), Math.abs(z - cz));
            // 바깥 테두리 · 동심 띠는 다듬은 돌, 그 사이는 섞어 깐 돌
            s.set(x, y, z, ring == maxX - cx || ring % 4 == 0 ? p.foundation().equals("cut_sandstone") ? "cut_sandstone" : "polished_andesite" : pickAt(p.road(), x, z));
            for (int dy = 1; dy <= Math.max(4, g - y); dy++) s.set(x, y + dy, z, "air");
            int dx = Math.abs(x - cx), dz = Math.abs(z - cz);
            if (dx <= 1 && dz <= 1) {   // 분수를 못 세우면 남는 우물
                s.set(x, y, z, dx == 0 && dz == 0 ? "water" : p.foundation());
                if (dx == 1 || dz == 1) s.set(x, y + 1, z, p.foundation());
            }
        }
    }

    /** 돌길: 길 재질을 섞어 깔고 (자갈 · 이끼 돌 · 다듬은 돌), 위 4 칸을 비운다 */
    static final class Road extends Structure {
        private final Palette p;

        Road(String region, int x1, int z1, int x2, int z2, Palette p) {
            super(Kind.ROAD, region, x1, z1, x2, z2);
            this.p = p;
        }

        @Override
        public void column(int x, int z, IntBinaryHeight ground, Sink s) {
            int y = ground.at(x, z);
            s.set(x, y, z, pickAt(p.road(), x, z));
            s.set(x, y - 1, z, p.foundation());
            for (int dy = 1; dy <= 4; dy++) s.set(x, y + dy, z, "air");
        }
    }

    /**
     * 성벽: 두께 3 · 높이 8 의 돌벽 + 바깥쪽 성가퀴 + 안쪽 통로. 모서리와 길 사이마다 5×5 탑 (높이 13, 꼭대기 성가퀴 · 랜턴),
     * 길이 지나는 곳은 성문 (폭 5 · 높이 5, 아치 + 문 옆 탑). NPC 자리 둘레는 비운다
     */
    static final class Wall extends Structure {
        private final int cx, cz, r, radius;
        private final Palette p;
        private final List<int[]> clear = new ArrayList<>();

        Wall(String region, int cx, int cz, int r, int radius, Palette p, List<int[]> keepClear) {
            super(Kind.WALL, region, cx - r - 2, cz - r - 2, cx + r + 2, cz + r + 2);
            this.cx = cx;
            this.cz = cz;
            this.r = r;
            this.radius = radius;
            this.p = p;
            for (int[] k : keepClear) if (Math.abs(Math.max(Math.abs(k[0] - cx), Math.abs(k[1] - cz)) - r) <= 4) clear.add(k);
        }

        /** 이 열의 {along, off(바깥 +), tower 중심 along} */
        private int[] frame(int x, int z) {
            int ax = x - cx, az = z - cz;
            int dist = Math.max(Math.abs(ax), Math.abs(az));
            int along = Math.abs(az) >= Math.abs(ax) ? ax : az;
            return new int[]{along, dist - r};
        }

        private boolean gate(int along) {
            int m = Math.floorMod(along + 16, 32) - 16;
            return Math.abs(m) <= 2 && Math.abs(along) < radius - 8;
        }

        /** 가장 가까운 탑 중심 along (모서리 = ±r, 길 사이 = 16 + 32k, 성문 양옆 = 길 ± 5) */
        private int tower(int along) {
            int best = Integer.MAX_VALUE;
            int[] cands = {r, -r, Math.floorDiv(along, 32) * 32 + 16, Math.floorDiv(along, 32) * 32 - 16,
                    Math.round(along / 32f) * 32 + 5, Math.round(along / 32f) * 32 - 5};
            for (int c : cands) {
                if (Math.abs(c) > r) continue;
                boolean side = Math.floorMod(c, 32) == 5 || Math.floorMod(c, 32) == 27;
                if (side && !gate(c + (Math.floorMod(c, 32) == 5 ? -5 : 5))) continue;
                if (!side && Math.abs(c) != r && Math.abs(c) > r - 6) continue;
                if (Math.abs(c - along) < Math.abs(best - along)) best = c;
            }
            return best;
        }

        @Override
        public boolean covers(int x, int z) {
            if (!super.covers(x, z)) return false;
            int[] f = frame(x, z);
            return Math.abs(f[1]) <= 2;
        }

        @Override
        public void column(int x, int z, IntBinaryHeight ground, Sink s) {
            int[] f = frame(x, z);
            int along = f[0], off = f[1];
            if (Math.abs(off) > 2) return;
            for (int[] k : clear) if (Math.abs(k[0] - x) <= 3 && Math.abs(k[1] - z) <= 3) return;
            int g = ground.at(x, z);
            int t = tower(along);
            boolean corner = Math.abs(x - cx) >= r - 2 && Math.abs(z - cz) >= r - 2;
            boolean inTower = corner ? Math.abs(Math.abs(x - cx) - r) <= 2 && Math.abs(Math.abs(z - cz) - r) <= 2 : Math.abs(along - t) <= 2;
            List<String> stone = p.stone();
            if (inTower) {
                boolean ns = Math.abs(z - cz) >= Math.abs(x - cx);
                int tx = corner || !ns ? cx + Integer.signum(x - cx) * r : cx + t;
                int tz = corner || ns ? cz + Integer.signum(z - cz) * r : cz + t;
                int base = ground.at(tx, tz);
                boolean edge = corner ? (Math.abs(Math.abs(x - cx) - r) == 2 || Math.abs(Math.abs(z - cz) - r) == 2) : (Math.abs(along - t) == 2 || Math.abs(off) == 2);
                for (int y = Math.min(g, base) - 3; y <= base + 12; y++) s.set(x, y, z, edge || y < base || y == base + 12 ? pickAt(stone, x * 31 + y, z) : "air");
                s.set(x, base, z, edge ? pickAt(stone, x, z) : p.floor());
                if (edge && (x == tx || z == tz)) for (int y = base + 5; y <= base + 9; y += 4) s.set(x, y, z, "air");   // 화살 구멍
                if (edge) s.set(x, base + 13, z, Math.floorMod(x + z, 2) == 0 ? pickAt(stone, x, z) : "air");
                else s.set(x, base + 13, z, x == tx && z == tz ? "lantern" : "air");
                for (int y = base + 14; y <= base + 16; y++) s.set(x, y, z, "air");
                return;
            }
            if (Math.abs(off) == 2) return;   // 탑만 두께 5
            boolean gate = gate(along);
            for (int y = g - 3; y <= g; y++) s.set(x, y, z, p.foundation());
            for (int dy = 1; dy <= 7; dy++) {
                String m = pickAt(stone, x * 7 + dy, z);
                if (gate && dy <= 5) m = "air";
                if (gate && dy == 5 && Math.abs(Math.floorMod(along + 16, 32) - 16) == 2) m = "stone_brick_stairs[facing=" + archFacing(x, z, along) + ",half=top,shape=straight]";
                s.set(x, g + dy, z, m);
            }
            // 성가퀴: 바깥줄은 하나 걸러 하나, 안쪽은 통로
            if (off == 1) s.set(x, g + 8, z, Math.floorMod(along, 2) == 0 ? pickAt(stone, x, z) : "air");
            else s.set(x, g + 8, z, "air");
            for (int dy = 9; dy <= 10; dy++) s.set(x, g + dy, z, "air");
            if (gate && off == 0 && Math.floorMod(along + 16, 32) - 16 == 0) s.set(x, g + 5, z, "lantern[hanging=true]");
        }

        /** 성문 아치 양끝 계단이 문 안쪽을 보게 */
        private String archFacing(int x, int z, int along) {
            boolean ns = Math.abs(z - cz) >= Math.abs(x - cx);   // 북 · 남 벽이면 along = x
            int m = Math.floorMod(along + 16, 32) - 16;
            if (ns) return m < 0 ? "west" : "east";
            return m < 0 ? "north" : "south";
        }
    }

    /** 지역 상자의 긴 축을 따라 가운데에 쌓는 큰 방벽. 200 블록마다 통로가 있다 (sealed 면 없음) */
    static final class LongWall extends Structure {
        private final boolean alongX, sealed;
        private final int mid;
        private final Style st;

        LongWall(Region r, Style st) {
            super(Kind.WALL, r.id(), r.minX(), r.minZ(), r.maxX(), r.maxZ());
            this.alongX = r.maxX() - r.minX() >= r.maxZ() - r.minZ();
            this.sealed = r.tags().contains("sealed");   // 마법의 장벽: 통로 없음
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
            boolean gate = !sealed && Math.floorMod(along, 200) >= 97 && Math.floorMod(along, 200) <= 103;
            int y = ground.at(x, z);
            for (int dy = 1; dy <= 10; dy++) s.set(x, y + dy, z, gate && dy <= 6 ? "AIR" : st.wall());
            if (off != 0 && along % 2 == 0) s.set(x, y + 11, z, st.wall());   // 성가퀴
            if (Math.floorMod(along, 50) == 0) for (int dy = 1; dy <= 14; dy++) s.set(x, y + dy, z, st.corner());   // 망루 기둥
        }
    }

    // ------------------------------------------------------------------ 랜드마크
    enum Shape { VOLCANO_CORE, FLOATING_ISLAND, TOWER, LIGHTHOUSE, SPIRE, STATUE, ANVIL, OASIS, KEEP, WATCHTOWER, COLOSSUS_HEAD, ARCH, ICE_SPIRE, OBELISK, STONE_CIRCLE, WINDMILL, ENTRANCE, TOWER_STUMP, PORTAL, WORLD_TREE }

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
            Map.entry("desert_of_tranquility", Shape.OASIS),
            // 위치를 이 게임이 정한 곳
            Map.entry("glacier_grotto", Shape.ICE_SPIRE), Map.entry("basic_training_hall", Shape.KEEP), Map.entry("novice_training_hall", Shape.KEEP),
            Map.entry("hero_tower", Shape.SPIRE), Map.entry("world_tree_scion", Shape.WORLD_TREE));

    public static Set<String> landmarkRegions() {
        return LANDMARKS.keySet();
    }

    private static Structure landmark(Region r, Style st, int cx, int cz, boolean town) {
        Shape shape = LANDMARKS.get(r.id());
        if (shape == null && r.tags().contains("portal")) shape = Shape.PORTAL;           // 다른 땅 · 차원으로 가는 문 (gates.yml)
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
            case WORLD_TREE -> 13;
            case PORTAL -> 6;
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
                case PORTAL -> { // 우는 흑요석 문틀 + 자수정 바닥. 안쪽은 비어 있어 걸어 들어간다 (이동은 서버가 문 지역으로 판정)
                    if (Math.abs(dx) <= 4 && Math.abs(dz) <= 4) s.set(x, y0, z, "AMETHYST_BLOCK");
                    if (dz == 0 && Math.abs(dx) <= 3) { boolean frame = Math.abs(dx) == 3;
                        fill(s, x, z, y0 + 1, y0 + 7, frame ? "CRYING_OBSIDIAN" : "AIR"); s.set(x, y0 + 8, z, "CRYING_OBSIDIAN"); } }
                case WORLD_TREE -> { // 세계수의 후손: 굵은 줄기 + 둥근 수관
                    if (d <= 3) fill(s, x, z, y0, y0 + 36, "OAK_WOOD");
                    for (int y = y0 + 26; y <= y0 + 48; y++) { double e = Math.hypot(d, (y - (y0 + 38)) * 1.2);
                        if (e <= 13 && d > 3 - (y > y0 + 36 ? 4 : 0)) s.set(x, y, z, "OAK_LEAVES"); } }
                case WINDMILL -> { if (d <= 3) fill(s, x, z, y0, y0 + 14, d > 2 ? "STONE_BRICKS" : "AIR");
                    if (dz == -4 && Math.abs(dx) <= 5) s.set(x, y0 + 12, z, "WHITE_WOOL");
                    if (dz == -4 && dx == 0) fill(s, x, z, y0 + 7, y0 + 17, "WHITE_WOOL"); }
            }
        }
    }
}
