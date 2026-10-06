package io.versaera.domain.terrain;

import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;

import java.util.Set;

/**
 * 지역 데이터로 지형을 만드는 모델 (WLD-02, ORIGINAL · 순수 계산). 원작 지도를 베끼지 않고 regions.yml 의 성격(tags)만 쓴다.
 * 높이 = 지역 성격의 기본 높이 + 진폭 × 노이즈, 경계는 주변 표본을 섞어 부드럽게 잇는다.
 * 분화구는 가운데가 꺼진 그릇, 바다는 해수면 아래, 도시는 거의 평평하다.
 */
public final class TerrainModel {
    public static final int SEA_LEVEL = 62;

    public enum Surface { GRASS, SAND, SNOW, STONE, PODZOL, MUD, GRAVEL, RED_SAND, DIRT_PATH, BASALT }

    private record Shape(double base, double amp, double rough, Surface surface) {}

    private final RegionIndex regions;
    private final String world;
    private final long seed;

    public TerrainModel(RegionIndex regions, String world, long seed) {
        this.regions = regions;
        this.world = world;
        this.seed = seed;
    }

    private static Shape shape(Region r) {
        if (r == null) return new Shape(70, 6, 0.004, Surface.GRASS);
        Set<String> t = r.tags();
        if (t.contains("sea")) return new Shape(38, 8, 0.01, Surface.GRAVEL);
        if (t.contains("volcano")) return new Shape(72, 6, 0.01, Surface.BASALT);
        if (t.contains("lake")) return new Shape(66, 2, 0.004, Surface.GRASS);
        if (t.contains("swamp")) return new Shape(63, 2, 0.02, Surface.MUD);
        if (t.contains("canyon") || t.contains("valley")) return new Shape(82, 12, 0.008, t.contains("forest") ? Surface.PODZOL : Surface.STONE);
        if (t.contains("mountain")) return new Shape(110, 48, 0.006, Surface.STONE);
        if (t.contains("city") || t.contains("outpost") || t.contains("fortress")) return new Shape(t.contains("coast") ? 65 : 68, 1.5, 0.01, Surface.DIRT_PATH);
        if (t.contains("coast")) return new Shape(63, 4, 0.01, Surface.SAND);
        if (t.contains("mist")) return new Shape(84, 26, 0.012, Surface.PODZOL);
        if (t.contains("frozen")) return new Shape(76, 18, 0.008, Surface.SNOW);
        if (t.contains("crater")) return new Shape(80, 6, 0.006, Surface.BASALT);
        if (t.contains("badlands")) return new Shape(74, 18, 0.01, Surface.RED_SAND);
        if (t.contains("desert")) return new Shape(75, 11, 0.006, Surface.SAND);
        if (t.contains("highland")) return new Shape(90, 26, 0.005, Surface.GRASS);
        if (t.contains("forest") || t.contains("frontier")) return new Shape(74, 12, 0.008, Surface.PODZOL);
        if (t.contains("ruins")) return new Shape(70, 6, 0.01, Surface.GRAVEL);
        if (t.contains("farmland")) return new Shape(67, 2, 0.004, Surface.GRASS);
        return new Shape(70, 6, 0.004, Surface.GRASS);
    }

    private Region regionAt(int x, int z) {
        Region r = regions.at(world, x, 64, z);
        // 64 높이를 안 덮는 지역(지하 · 낮은 유적)은 지표 모양에 쓰지 않고 그 위 지역을 쓴다
        // 랜드마크 · 던전 입구 · 성벽 같은 작은 표시 지역도 땅 모양은 바꾸지 않는다 (구조물은 SettlementPlanner 가 놓는다)
        while (r != null && (r.maxY() < 64 || markerOnly(r)) && r.parent() != null) r = regions.byId(r.parent());
        return r != null && (r.maxY() < 64 || markerOnly(r)) ? null : r;
    }

    private static final Set<String> MARKERS = Set.of("landmark", "dungeon_site", "wall");

    private static boolean markerOnly(Region r) {
        return !r.tags().isEmpty() && MARKERS.containsAll(r.tags());
    }

    // ------------------------------------------------------------------ 노이즈 (결정적 값 노이즈, 외부 라이브러리 없음)
    private double hash(long x, long z, int octave) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (octave * 0x165667B19E3779F9L);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53) * 2 - 1;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private double value(double x, double z, int octave) {
        long x0 = (long) Math.floor(x), z0 = (long) Math.floor(z);
        double fx = smooth(x - x0), fz = smooth(z - z0);
        double a = hash(x0, z0, octave), b = hash(x0 + 1, z0, octave), c = hash(x0, z0 + 1, octave), d = hash(x0 + 1, z0 + 1, octave);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    /** -1 ~ 1 근처의 프랙탈 노이즈 */
    public double noise(double x, double z, double freq) {
        double sum = 0, amp = 1, norm = 0;
        for (int o = 0; o < 4; o++) {
            sum += value(x * freq, z * freq, o) * amp;
            norm += amp;
            amp *= 0.5;
            freq *= 2;
        }
        return sum / norm;
    }

    // ------------------------------------------------------------------ 높이 · 표면
    private static final int BLEND = 24;

    private static final int GRID = 32;

    /** 격자점의 지형 성격 = 주변 5 표본의 가중 평균 → {기본 높이, 진폭, 거칠기} */
    private double[] blendAt(int gx, int gz) {
        double base = 0, amp = 0, rough = 0, total = 0;
        int[][] samples = {{0, 0}, {BLEND, 0}, {-BLEND, 0}, {0, BLEND}, {0, -BLEND}};
        double[] weight = {2, 1, 1, 1, 1};
        for (int i = 0; i < samples.length; i++) {
            Shape s = shape(regionAt(gx + samples[i][0], gz + samples[i][1]));
            base += s.base() * weight[i];
            amp += s.amp() * weight[i];
            rough += s.rough() * weight[i];
            total += weight[i];
        }
        return new double[]{base / total, amp / total, rough / total};
    }

    public int height(int x, int z) {
        // 32 블록 격자점의 성격을 겹선형으로 이어 경계에서 높이가 계단처럼 튀지 않게 한다
        int gx = Math.floorDiv(x, GRID) * GRID, gz = Math.floorDiv(z, GRID) * GRID;
        double fx = (x - gx) / (double) GRID, fz = (z - gz) / (double) GRID;
        double[] a = blendAt(gx, gz), b = blendAt(gx + GRID, gz), c = blendAt(gx, gz + GRID), d = blendAt(gx + GRID, gz + GRID);
        double[] p = new double[3];
        for (int i = 0; i < 3; i++) p[i] = (a[i] * (1 - fx) + b[i] * fx) * (1 - fz) + (c[i] * (1 - fx) + d[i] * fx) * fz;
        double base = p[0], amp = p[1], rough = p[2];
        double h = base + amp * noise(x, z, rough);
        Region r = regionAt(x, z);
        if (r != null && r.tags().contains("crater")) h -= craterDepth(r, x, z);
        if (r != null && r.tags().contains("river")) h = riverCarve(r, x, z, h);
        if (r != null && r.tags().contains("lake")) h = Math.min(h, h - lakeDepth(r, x, z));
        if (r != null && (r.tags().contains("canyon") || r.tags().contains("valley"))) h -= cut(r, x, z, r.tags().contains("canyon") ? 48 : 22);
        if (r != null && r.tags().contains("volcano")) h += volcano(r, x, z);
        if (r != null && r.tags().contains("hole")) h -= hole(r, x, z);
        return (int) Math.round(Math.max(-50, Math.min(250, h)));
    }

    private static double radial(Region r, int x, int z) {
        double cx = (r.minX() + r.maxX()) / 2.0, cz = (r.minZ() + r.maxZ()) / 2.0;
        double rx = (r.maxX() - r.minX()) / 2.0, rz = (r.maxZ() - r.minZ()) / 2.0;
        return Math.hypot((x - cx) / rx, (z - cz) / rz);
    }

    /** 물이 차지 않는 곳 (거대한 구멍) */
    public boolean dry(int x, int z) {
        Region r = regionAt(x, z);
        return r != null && r.tags().contains("hole");
    }

    /** 거대한 구멍: 가장자리는 절벽, 가운데는 100 블록 가까이 꺼진다 */
    private static double hole(Region r, int x, int z) {
        double d = radial(r, x, z);
        if (d >= 1) return 0;
        return d < 0.7 ? 100 : 100 * (1 - (d - 0.7) / 0.3);
    }

    /** 호수: 타원 안쪽이 해수면 아래로 (물이 찬다), 가장자리는 완만 */
    private static double lakeDepth(Region r, int x, int z) {
        double d = radial(r, x, z);
        if (d >= 1) return 0;
        return d < 0.8 ? 10 : 10 * (1 - (d - 0.8) / 0.2);
    }

    /** 골짜기 · 협곡: 긴 방향을 따라 가운데가 깊게 파인 띠 (협곡은 벽이 가파르다) */
    private static double cut(Region r, int x, int z, double depth) {
        boolean northSouth = (r.maxZ() - r.minZ()) >= (r.maxX() - r.minX());
        double c = northSouth ? (r.minX() + r.maxX()) / 2.0 : (r.minZ() + r.maxZ()) / 2.0;
        double half = (northSouth ? r.maxX() - r.minX() : r.maxZ() - r.minZ()) / 2.0;
        double k = Math.abs((northSouth ? x : z) - c) / (half * 0.6);
        if (k >= 1) return 0;
        double steep = depth > 30 ? Math.pow(1 - k, 0.35) : 1 - k * k;
        return depth * steep;
    }

    /** 화산: 지역 가운데로 솟은 원뿔 + 꼭대기 분화구 */
    private static double volcano(Region r, int x, int z) {
        double d = radial(r, x, z);
        if (d >= 1) return 0;
        if (d < 0.12) return 88;   // 꼭대기 분화구 바닥 (가장자리보다 12 낮음)
        return 100 * (1 - d) / 0.88;
    }

    /** 분화구: 지역 가운데로 갈수록 깊어지는 그릇 (가장자리는 둔덕) */
    private static double craterDepth(Region r, int x, int z) {
        double cx = (r.minX() + r.maxX()) / 2.0, cz = (r.minZ() + r.maxZ()) / 2.0;
        double rad = Math.min(r.maxX() - r.minX(), r.maxZ() - r.minZ()) / 2.0;
        double d = Math.hypot(x - cx, z - cz) / rad;
        if (d >= 1) return 0;
        if (d > 0.85) return -8 * (1 - (d - 0.85) / 0.15);   // 둔덕
        return 46 * (1 - d * d);
    }

    /**
     * 강: 지역의 긴 방향을 따라 굽이치는 물길 하나 (예: 브리튼 연합의 루카 강은 남북, 그라디안 들판의 강은 동서).
     * 폭 약 16 블록, 가장자리는 완만한 둑.
     */
    private double riverCarve(Region r, int x, int z, double h) {
        boolean northSouth = (r.maxZ() - r.minZ()) >= (r.maxX() - r.minX());
        double cx = (r.minX() + r.maxX()) / 2.0, cz = (r.minZ() + r.maxZ()) / 2.0;
        double phase = (r.id().hashCode() & 1023) / 163.0;
        double amp = Math.min(160, (northSouth ? r.maxX() - r.minX() : r.maxZ() - r.minZ()) / 5.0);
        double dist = northSouth
                ? Math.abs(x - (cx + amp * Math.sin(z / 420.0 + phase) + amp * 0.3 * Math.sin(z / 130.0)))
                : Math.abs(z - (cz + amp * Math.sin(x / 420.0 + phase) + amp * 0.3 * Math.sin(x / 130.0)));
        if (dist > 14) return h;
        if (dist <= 8) return Math.min(h, SEA_LEVEL - 3);
        double k = (dist - 8) / 6.0;   // 둑
        return Math.min(h, SEA_LEVEL - 3 + k * (h - SEA_LEVEL + 3));
    }

    public Surface surface(int x, int z, int height) {
        Region r = regionAt(x, z);
        Surface s = shape(r).surface();
        if (height < SEA_LEVEL - 1 && s != Surface.GRAVEL) return Surface.SAND;
        if (height > 150 && s != Surface.SNOW) return Surface.STONE;
        if (s == Surface.SAND && r != null && r.tags().contains("desert") && noise(x, z, 0.02) > 0.45) return Surface.RED_SAND;
        return s;
    }

    /** 유적 지역의 부서진 기둥 (64 블록 칸마다 결정적으로 0~1개). 반환: {x, z, 높이} 또는 null */
    public int[] ruinPillar(int cellX, int cellZ) {
        int x = cellX * 64 + 8 + (int) Math.floor((hash(cellX, cellZ, 9) + 1) * 24);
        int z = cellZ * 64 + 8 + (int) Math.floor((hash(cellZ, cellX, 10) + 1) * 24);
        Region r = regionAt(x, z);
        if (r == null || !r.tags().contains("ruins")) return null;
        if (hash(cellX, cellZ, 11) < 0.1) return null;
        return new int[]{x, z, 4 + (int) Math.floor((hash(cellX, cellZ, 12) + 1) * 5)};
    }
}
