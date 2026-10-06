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
        if (t.contains("mountain")) return new Shape(110, 48, 0.006, Surface.STONE);
        if (t.contains("city") || t.contains("outpost") || t.contains("fortress")) return new Shape(t.contains("coast") ? 65 : 68, 1.5, 0.01, Surface.DIRT_PATH);
        if (t.contains("coast")) return new Shape(63, 4, 0.01, Surface.SAND);
        if (t.contains("mist")) return new Shape(84, 26, 0.012, Surface.PODZOL);
        if (t.contains("frozen")) return new Shape(76, 18, 0.008, Surface.SNOW);
        if (t.contains("crater")) return new Shape(80, 6, 0.006, Surface.BASALT);
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
        while (r != null && r.maxY() < 64 && r.parent() != null) r = regions.byId(r.parent());
        return r != null && r.maxY() < 64 ? null : r;
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

    public int height(int x, int z) {
        double base = 0, amp = 0, rough = 0;
        int[][] samples = {{0, 0}, {BLEND, 0}, {-BLEND, 0}, {0, BLEND}, {0, -BLEND}};
        double[] weight = {2, 1, 1, 1, 1};
        double total = 0;
        for (int i = 0; i < samples.length; i++) {
            Shape s = shape(regionAt(x + samples[i][0], z + samples[i][1]));
            base += s.base() * weight[i];
            amp += s.amp() * weight[i];
            rough += s.rough() * weight[i];
            total += weight[i];
        }
        base /= total;
        amp /= total;
        rough /= total;
        double h = base + amp * noise(x, z, rough);
        Region r = regionAt(x, z);
        if (r != null && r.tags().contains("crater")) h -= craterDepth(r, x, z);
        if (r != null && r.tags().contains("river")) h = riverCarve(x, z, h);
        return (int) Math.round(Math.max(-50, Math.min(250, h)));
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

    /** 강: 노이즈의 0 근처 띠를 해수면 아래로 판다 */
    private double riverCarve(int x, int z, double h) {
        double n = Math.abs(noise(x + 9000, z - 9000, 0.0025));
        if (n > 0.06) return h;
        double k = n / 0.06;
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
