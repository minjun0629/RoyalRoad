package io.versaera.domain.terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 건물 설계도 (순수 데이터). 상자 (w × h × d) 안의 블록을 팔레트 번호로 담는다 — 건물 하나에 수 KB.
 * 블록은 "stone_bricks" 같은 id 나 "oak_stairs[facing=north,half=bottom]" 같은 상태 문자열 (minecraft: 생략).
 * 0 번 = 비어 있음 (지형 그대로). 앞(문)은 북쪽(-z, z = 0 쪽)을 기준으로 그리고, 세울 때 돌린다.
 */
public final class Blueprint {
    public final int w, h, d;
    private final byte[] cells;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();

    public Blueprint(int w, int h, int d) {
        if (w <= 0 || h <= 0 || d <= 0 || (long) w * h * d > 400_000) throw new IllegalArgumentException("설계도 크기: " + w + "x" + h + "x" + d);
        this.w = w;
        this.h = h;
        this.d = d;
        this.cells = new byte[w * h * d];
        palette.add(null);
    }

    public boolean in(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < w && y < h && z < d;
    }

    public void set(int x, int y, int z, String block) {
        if (!in(x, y, z)) return;
        int i;
        if (block == null) i = 0;
        else {
            Integer k = index.get(block);
            if (k == null) {
                if (palette.size() >= 255) throw new IllegalStateException("설계도 팔레트가 가득 찼습니다");
                k = palette.size();
                palette.add(block);
                index.put(block, k);
            }
            i = k;
        }
        cells[x + w * (z + d * y)] = (byte) i;
    }

    public String get(int x, int y, int z) {
        if (!in(x, y, z)) return null;
        return palette.get(cells[x + w * (z + d * y)] & 0xff);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) set(x, y, z, block);
    }

    /** 비어 있는 칸에만 */
    public void soft(int x, int y, int z, String block) {
        if (get(x, y, z) == null) set(x, y, z, block);
    }

    /** 시계 방향(위에서 볼 때)으로 quarter 번 돌린 사본. 계단 · 문 · 덧문 방향 · 통나무 축도 함께 돈다 */
    public Blueprint rotated(int quarter) {
        int q = Math.floorMod(quarter, 4);
        if (q == 0) return this;
        Blueprint r = new Blueprint(d, h, w);
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++) {
                    String b = get(x, y, z);
                    if (b != null) r.set(d - 1 - z, y, x, rotateBlock(b, 1));   // (x, z) → (d-1-z, x)
                }
        return r.rotated(q - 1);
    }

    private static final String[] FACING = {"north", "east", "south", "west"};

    /** 블록 상태 문자열을 시계 방향으로 돌린다: facing · axis · 울타리/판유리 연결(north/east/…) */
    public static String rotateBlock(String b, int quarter) {
        int q = Math.floorMod(quarter, 4);
        if (q == 0 || b.indexOf('[') < 0) return b;
        int open = b.indexOf('['), close = b.lastIndexOf(']');
        String id = b.substring(0, open);
        String[] props = b.substring(open + 1, close).split(",");
        Map<String, String> sides = new HashMap<>();
        List<String> out = new ArrayList<>();
        for (String p : props) {
            int eq = p.indexOf('=');
            String k = p.substring(0, eq), v = p.substring(eq + 1);
            switch (k) {
                case "facing" -> {
                    int i = List.of(FACING).indexOf(v);
                    out.add(i < 0 ? p : "facing=" + FACING[(i + q) % 4]);
                }
                case "axis" -> out.add(q % 2 == 1 && !v.equals("y") ? "axis=" + (v.equals("x") ? "z" : "x") : p);
                case "north", "east", "south", "west" -> sides.put(k, v);
                default -> out.add(p);
            }
        }
        for (Map.Entry<String, String> e : sides.entrySet()) out.add(FACING[(List.of(FACING).indexOf(e.getKey()) + q) % 4] + "=" + e.getValue());
        return id + "[" + String.join(",", out) + "]";
    }
}
