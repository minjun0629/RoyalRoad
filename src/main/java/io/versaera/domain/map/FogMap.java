package io.versaera.domain.map;

import io.versaera.domain.world.Region;

import java.util.function.BiPredicate;

/**
 * 탐험 지도 (MAP-01, ORIGINAL). 직접 가 본 칸(CELL × CELL 블록)만 보이고 나머지는 안개.
 * 출력은 128 × 128 의 색 번호 (플랫폼이 지도 색으로 바꾼다). 순수 계산.
 */
public final class FogMap {
    public static final int CELL = 32, SIZE = 128;

    /** 지도 색 번호 */
    public enum Tone { FOG, PLAINS, FOREST, CITY, DESERT, SEA, MOUNTAIN, FROZEN, RUINS, MIST, BORDER, YOU, CRATER, DEEP }

    public interface RegionLookup {
        Region at(int x, int z);
    }

    private FogMap() {
    }

    public static int cell(int block) {
        return Math.floorDiv(block, CELL);
    }

    public static Tone tone(Region r) {
        if (r == null) return Tone.PLAINS;
        var t = r.tags();
        if (t.contains("mist")) return Tone.MIST;
        if (t.contains("city") || t.contains("outpost") || t.contains("fortress")) return Tone.CITY;
        if (t.contains("sea") || t.contains("coast")) return Tone.SEA;
        if (t.contains("desert")) return Tone.DESERT;
        if (t.contains("frozen")) return Tone.FROZEN;
        if (t.contains("crater")) return Tone.CRATER;
        if (t.contains("ruins")) return Tone.RUINS;
        if (t.contains("mountain") || t.contains("highland")) return Tone.MOUNTAIN;
        if (t.contains("forest") || t.contains("frontier")) return Tone.FOREST;
        if (t.contains("underground") || t.contains("deep")) return Tone.DEEP;
        return Tone.PLAINS;
    }

    /**
     * @param centerX 지도 가운데 (블록)
     * @param scale   한 픽셀 = scale 블록 (1 · 2 · 4 · 8 · 16)
     * @param explored (cx, cz) 를 가 봤는가
     * @param youX    플레이어 위치 (지도 밖이면 표시 안 함)
     */
    public static Tone[] render(int centerX, int centerZ, int scale, BiPredicate<Integer, Integer> explored, RegionLookup regions, int youX, int youZ) {
        Tone[] px = new Tone[SIZE * SIZE];
        int half = SIZE / 2;
        for (int pz = 0; pz < SIZE; pz++)
            for (int pxi = 0; pxi < SIZE; pxi++) {
                int bx = centerX + (pxi - half) * scale, bz = centerZ + (pz - half) * scale;
                if (!explored.test(cell(bx), cell(bz))) {
                    px[pz * SIZE + pxi] = Tone.FOG;
                    continue;
                }
                Region r = regions.at(bx, bz);
                Region right = regions.at(bx + scale, bz), down = regions.at(bx, bz + scale);
                boolean border = r != null && (right != r || down != r);
                px[pz * SIZE + pxi] = border ? Tone.BORDER : tone(r);
            }
        int yx = (youX - centerX) / scale + half, yz = (youZ - centerZ) / scale + half;
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                int x = yx + dx, z = yz + dz;
                if (x >= 0 && z >= 0 && x < SIZE && z < SIZE) px[z * SIZE + x] = Tone.YOU;
            }
        return px;
    }

    /** 이 위치에서 밝혀지는 칸들 (시야 반경 = 1칸 → 3 × 3) */
    public static long[] reveal(int x, int z) {
        int cx = cell(x), cz = cell(z);
        long[] out = new long[9];
        int i = 0;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) out[i++] = pack(cx + dx, cz + dz);
        return out;
    }

    public static long pack(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    public static int unpackX(long k) {
        return (int) (k >> 32);
    }

    public static int unpackZ(long k) {
        return (int) k;
    }
}
