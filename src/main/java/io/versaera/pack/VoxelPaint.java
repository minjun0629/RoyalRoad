package io.versaera.pack;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 큐브 모델의 면마다 손으로 칠한 듯한 텍스처 (Armourer's Workshop 의 음영 붓 · 노이즈 붓 · 하이라이트처럼).
 * 상자마다 단색이면 그림판처럼 보이므로, 면을 1/4 칸(모델 1 칸 = 4 픽셀)으로 나눠 재질대로 칠한다 —
 * 금속: 길이 방향 결 · 위 모서리 빛 · 아래 그늘 · 가는 흠집, 금장: 음각 테두리 · 빛 띠, 나무: 나뭇결, 가죽: 비스듬히 감은 줄,
 * 보석: 안쪽에서 빛나는 광택 · 반짝이는 점, 빛나는 것(룬 · 불): 가운데가 하얗게.
 * 모든 면을 한 장(아틀라스)에 선반식으로 담는다.
 */
public final class VoxelPaint {
    private VoxelPaint() {
    }

    /** 면 순서: 0 south(+z) 1 north 2 east(+x) 3 west 4 up 5 down */
    public static final String[] FACES = {"south", "north", "east", "west", "up", "down"};
    static final int PX = 4;

    /** @param rects 상자 · 면마다 아틀라스 위 {x, y, w, h} (픽셀) */
    public record Painted(BufferedImage atlas, int[][][] rects) {
        public int size() {
            return atlas.getWidth();
        }
    }

    enum Mat { METAL, EDGE, GOLD, WOOD, LEATHER, GEM, GLOW, BONE, CLOTH }

    /** 색으로 재질을 고른다 (VoxelSmith 의 색은 모두 재질 톤에서 나온다) */
    static Mat mat(Color c) {
        float[] h = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float hue = h[0] * 360, sat = h[1], val = h[2];
        if (val > 0.93 && sat > 0.35) return Mat.GLOW;
        if (sat > 0.55 && val > 0.45 && !(hue > 20 && hue < 60)) return Mat.GEM;
        if (hue >= 35 && hue <= 60 && sat > 0.4 && val > 0.55) return Mat.GOLD;
        if (hue >= 10 && hue < 45 && sat > 0.3 && val < 0.62) return val < 0.38 ? Mat.LEATHER : Mat.WOOD;
        if (sat < 0.2 && val > 0.85) return Mat.EDGE;
        if (hue >= 30 && hue <= 60 && sat < 0.3 && val > 0.7) return Mat.BONE;
        if (sat < 0.25) return Mat.METAL;
        return Mat.CLOTH;
    }

    static int dimPx(double units) {
        return Math.max(1, (int) Math.round(units * PX));
    }

    /** 면의 가로 · 세로 (모델 칸) */
    static double[] faceSize(VoxelSmith.Box b, int f) {
        double dx = b.x1() - b.x0(), dy = b.y1() - b.y0(), dz = b.z1() - b.z0();
        return switch (f) {
            case 0, 1 -> new double[]{dx, dy};
            case 2, 3 -> new double[]{dz, dy};
            default -> new double[]{dx, dz};
        };
    }

    public static Painted paint(List<VoxelSmith.Box> boxes) {
        int n = boxes.size();
        int[][][] rects = new int[n][6][];
        List<int[]> order = new ArrayList<>();   // {box, face, w, h}
        for (int i = 0; i < n; i++)
            for (int f = 0; f < 6; f++) {
                double[] s = faceSize(boxes.get(i), f);
                order.add(new int[]{i, f, dimPx(s[0]), dimPx(s[1])});
            }
        order.sort(Comparator.comparingInt((int[] o) -> -o[3]).thenComparingInt(o -> -o[2]));
        int size = 64;
        while (!pack(order, rects, size)) size *= 2;
        BufferedImage atlas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double minY = boxes.stream().mapToDouble(VoxelSmith.Box::y0).min().orElse(0), maxY = boxes.stream().mapToDouble(VoxelSmith.Box::y1).max().orElse(16);
        for (int i = 0; i < n; i++)
            for (int f = 0; f < 6; f++) paintFace(atlas, rects[i][f], boxes.get(i), i, f, (boxes.get(i).y0() + boxes.get(i).y1()) / 2 - minY, maxY - minY);
        return new Painted(atlas, rects);
    }

    private static boolean pack(List<int[]> order, int[][][] rects, int size) {
        int x = 0, y = 0, row = 0;
        for (int[] o : order) {
            int w = o[2], h = o[3];
            if (w > size) return false;
            if (x + w > size) { x = 0; y += row; row = 0; }
            if (y + h > size) return false;
            rects[o[0]][o[1]] = new int[]{x, y, w, h};
            x += w;
            row = Math.max(row, h);
        }
        return true;
    }

    static int hash(int a, int b, int c, int d) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791 ^ d * 2654435;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    static double noise(int a, int b, int c, int d) {
        return ((hash(a, b, c, d) & 0xffff) / 65535.0) - 0.5;
    }

    static Color shade(Color c, double k) {
        if (k >= 0) return new Color(clamp(c.getRed() + (255 - c.getRed()) * k), clamp(c.getGreen() + (255 - c.getGreen()) * k), clamp(c.getBlue() + (255 - c.getBlue()) * k));
        return new Color(clamp(c.getRed() * (1 + k)), clamp(c.getGreen() * (1 + k)), clamp(c.getBlue() * (1 + k)));
    }

    static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    /**
     * 면 하나 칠하기. u = 가로 (0 왼쪽), v = 세로 (0 위). 옆면의 세로는 무기 길이 방향.
     * @param along 상자 가운데의 높이 (모델 아래에서), len = 모델 길이 — 끝으로 갈수록 살짝 밝게
     */
    static void paintFace(BufferedImage atlas, int[] r, VoxelSmith.Box b, int box, int f, double along, double len) {
        Color base = b.c();
        Mat m = mat(base);
        int w = r[2], h = r[3];
        boolean side = f < 4, top = f == 4, bottom = f == 5;
        double faceLight = top ? 0.12 : bottom ? -0.18 : 0;
        double lengthTint = (along / Math.max(1, len) - 0.5) * 0.08;
        int seed = hash(box, f, base.getRGB(), 7);
        for (int v = 0; v < h; v++)
            for (int u = 0; u < w; u++) {
                double k = faceLight + lengthTint;
                double fu = w == 1 ? 0.5 : u / (double) (w - 1), fv = h == 1 ? 0.5 : v / (double) (h - 1);
                boolean edgeL = u == 0 && w > 2, edgeR = u == w - 1 && w > 2, edgeT = v == 0 && h > 2, edgeB = v == h - 1 && h > 2;
                switch (m) {
                    case METAL, EDGE -> {
                        // 길이 방향으로 긁힌 결 (세로줄) + 고운 노이즈 + 모서리 빛 · 그늘
                        k += noise(seed, u, 0, 1) * (m == Mat.EDGE ? 0.06 : 0.12) + noise(seed, u, v, 2) * 0.05;
                        if (side) k += (0.5 - fu) * 0.28 + (0.5 - fv) * 0.08;                // 빛이 왼쪽 위에서: 면 안에서도 밝기가 흐른다
                        else k -= 0.06;
                        if (side && (edgeL || edgeT)) k += 0.16;
                        if (side && (edgeR || edgeB)) k -= 0.14;
                        if (side && Math.abs(fu - 0.3) < 0.12 && w > 3) k += 0.2;    // 빛 띠
                        if ((hash(seed, u, v, 3) & 255) < 4) k -= 0.25;                // 흠집
                    }
                    case GOLD -> {
                        k += (0.5 - fv) * 0.18 + noise(seed, u, v, 4) * 0.05;
                        boolean inset = w > 4 && h > 4 && (u == 1 || v == 1 || u == w - 2 || v == h - 2);
                        if (inset) k -= 0.32;                                           // 음각 테두리
                        if (edgeL || edgeT) k += 0.22;
                        if (edgeR || edgeB) k -= 0.18;
                        if (w > 4 && h > 4 && !inset && u > 1 && v > 1 && u < w - 2 && v < h - 2 && ((u + v) % 4 == 0)) k += 0.1;   // 잔 무늬
                    }
                    case WOOD -> {
                        double grain = Math.sin((u + Math.sin(v * 0.35 + seed % 7) * 1.6) * 1.9);
                        k += grain * 0.1 + noise(seed, u, v, 5) * 0.06;
                        if ((hash(seed, u, v / 3, 6) & 255) < 6) k -= 0.2;              // 옹이
                        if (edgeL || edgeT) k += 0.08;
                        if (edgeR || edgeB) k -= 0.12;
                    }
                    case LEATHER -> {
                        int band = Math.floorMod(u + v, 4);
                        k += band == 0 ? -0.22 : band == 1 ? 0.1 : 0;                  // 비스듬히 감은 끈
                        k += noise(seed, u, v, 7) * 0.08;
                    }
                    case GEM -> {
                        double d = Math.hypot(fu - 0.35, fv - 0.3);
                        k += 0.32 - d * 0.75;
                        if (u == Math.max(0, w / 3 - 1) && v == Math.max(0, h / 4)) k = 0.75;   // 반짝
                        if (edgeR || edgeB) k -= 0.25;
                    }
                    case GLOW -> {
                        double d = Math.hypot(fu - 0.5, fv - 0.5);
                        k = 0.45 - d * 0.6 + noise(seed, u, v, 8) * 0.05;
                    }
                    case BONE -> {
                        k += noise(seed, u, v / 2, 9) * 0.1 + (fv - 0.5) * -0.1;
                        if ((hash(seed, u, v, 10) & 255) < 10) k -= 0.18;              // 뼈 구멍
                        if (edgeR || edgeB) k -= 0.12;
                    }
                    case CLOTH -> {
                        k += ((u + v) % 2 == 0 ? 0.04 : -0.04) + noise(seed, u, v, 11) * 0.06;
                        if (edgeR || edgeB) k -= 0.1;
                    }
                }
                atlas.setRGB(r[0] + u, r[1] + v, shade(base, k).getRGB() | 0xff000000);
            }
    }

    /** 렌더러용: 상자 면 위 점 (모델 좌표) → 그 자리 텍스처 색 */
    static int sample(Painted p, int box, int f, VoxelSmith.Box b, double x, double y, double z) {
        int[] r = p.rects()[box][f];
        double dx = b.x1() - b.x0(), dy = b.y1() - b.y0(), dz = b.z1() - b.z0();
        double u, v;
        switch (f) {
            case 0 -> { u = (x - b.x0()) / dx; v = (b.y1() - y) / dy; }
            case 1 -> { u = (b.x1() - x) / dx; v = (b.y1() - y) / dy; }
            case 2 -> { u = (b.z1() - z) / dz; v = (b.y1() - y) / dy; }
            case 3 -> { u = (z - b.z0()) / dz; v = (b.y1() - y) / dy; }
            case 4 -> { u = (x - b.x0()) / dx; v = (z - b.z0()) / dz; }
            default -> { u = (x - b.x0()) / dx; v = (b.z1() - z) / dz; }
        }
        int px = r[0] + Math.min(r[2] - 1, Math.max(0, (int) (u * r[2]))), py = r[1] + Math.min(r[3] - 1, Math.max(0, (int) (v * r[3])));
        return p.atlas().getRGB(px, py);
    }
}
